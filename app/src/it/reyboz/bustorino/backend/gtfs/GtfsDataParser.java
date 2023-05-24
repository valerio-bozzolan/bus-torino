/*
	BusTO  - Backend components
    Copyright (C) 2021 Fabio Mazza

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.reyboz.bustorino.backend.gtfs;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;

import de.siegmar.fastcsv.reader.CloseableIterator;
import de.siegmar.fastcsv.reader.NamedCsvReader;
import de.siegmar.fastcsv.reader.NamedCsvRow;
import it.reyboz.bustorino.backend.Fetcher;
import it.reyboz.bustorino.backend.networkTools;
import it.reyboz.bustorino.data.gtfs.CsvTableInserter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

abstract public class GtfsDataParser {
    public static final String GTFS_ADDRESS="https://www.gtt.to.it/open_data/gtt_gtfs.zip";
    public static final String GTFS_PAGE_ADDRESS="http://aperto.comune.torino.it/dataset/feed-gtfs-trasporti-gtt";

    private static final String DEBUG_TAG = "BusTO-GTFSDataParser";

    private static final Pattern quotePattern = Pattern.compile("^\\s*\"((?:[^\"]|(?:\"\"))*?)\"\\s*,");

    /**
     * First trial for a function to download the zip
     * @param res Fetcher.result
     * @return the list of files inside the ziè
     */
    public static ArrayList<String> readFilesList(AtomicReference<Fetcher.Result> res){

        HttpURLConnection urlConnection;
        InputStream in;
        ArrayList<String> result = new ArrayList<>();
        try {
            final URL gtfsUrl = new URL(GTFS_ADDRESS);
            urlConnection = (HttpURLConnection) gtfsUrl.openConnection();
        } catch(IOException e) {
            //e.printStackTrace();
            res.set(Fetcher.Result.SERVER_ERROR); // even when offline, urlConnection works fine. WHY.
            return null;
        }
        urlConnection.setConnectTimeout(4000);
        urlConnection.setReadTimeout(50*1000);

        try {
            in = urlConnection.getInputStream();
        } catch (Exception e) {
            try {
                if(urlConnection.getResponseCode()==404)
                    res.set(Fetcher.Result.SERVER_ERROR_404);
            } catch (IOException e2) {
                e2.printStackTrace();
            }
            return null;

        }
        try (ZipInputStream stream = new ZipInputStream(in)) {

            // now iterate through each item in the stream. The get next
            // entry call will return a ZipEntry for each file in the
            // stream
            ZipEntry entry;
            while ((entry = stream.getNextEntry()) != null) {
                String s = String.format(Locale.ENGLISH, "Entry: %s len %d added",
                        entry.getName(),
                        entry.getSize()
                );
                System.out.println(s);

                // Once we get the entry from the stream, the stream is
                // positioned read to read the raw data, and we keep
                // reading until read returns 0 or less.
                result.add(entry.getName());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        // we must always close the zip file.
        return result;
    }

    public static Date getLastGTFSUpdateDate(AtomicReference<Fetcher.Result> res) {
        URL theURL;
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH);
        //final Date baseDate = dateFormat.parse("1970-00-00T00:00:00+0000");
        final Date nullDate = new Date(0);
        try{
            theURL = new URL(GTFS_PAGE_ADDRESS);
        } catch (IOException ex){
            Log.e(DEBUG_TAG, "Fixed URL is null, this is a real issue");
            return nullDate;
        }
        res.set(Fetcher.Result.OK);
        final String fullPageDOM = networkTools.getDOM(theURL, res);
        if(fullPageDOM== null){
            //Something wrong happend
            Log.e(DEBUG_TAG, "Cannot get URL");
            return nullDate;
        }
        res.set(Fetcher.Result.OK);
        Document doc = Jsoup.parse(fullPageDOM);

        Elements sections = doc.select("section.additional-info");
        Date finalDate = new Date(0);
        for (Element sec: sections){
            Element head = sec.select("h3").first();
            String headTitle = head.text();
            if(!headTitle.trim().toLowerCase(Locale.ITALIAN).equals("informazioni supplementari"))
                continue;

            for (Element row: sec.select("tr")){
                if(!row.selectFirst("th").text().trim()
                        .toLowerCase(Locale.ITALIAN).equals("ultimo aggiornamento"))
                    continue;

                Attributes spanAttributes = row.selectFirst("td > span").attributes();
                String dateAsString = spanAttributes.get("data-datetime");
                try {
                    finalDate = dateFormat.parse(dateAsString);
                    return finalDate;
                }catch (ParseException ex){
                    Log.e(DEBUG_TAG, "Wrong date for the last update of GTFS Data: "+dateAsString);
                    res.set(Fetcher.Result.PARSER_ERROR);
                    ex.printStackTrace();
                }
                break;
            }
        }
        res.set(Fetcher.Result.PARSER_ERROR);
        return finalDate;

    }
    public static void readGtfsZipEntry(ZipEntry entry, ZipFile zipFile, Context  con) throws IOException{
        String tableName = entry.getName().split("\\.")[0].trim();
        InputStream stream = zipFile.getInputStream(entry);
        String s = String.format(Locale.ENGLISH, "Entry: %s len %d added",
                entry.getName(),
                entry.getSize()
        );
        Log.d(DEBUG_TAG, s);
        final BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        GtfsDataParser.readCSVWithColumns(reader, tableName, con);
        stream.close();
    }

    public static void readCSVWithColumns(BufferedReader reader, String tableName, Context con) {

        //String[] elements;
        List<String> lineElements;

        String line;

        /*final String header = reader.readLine();
        if (header == null){
            throw new IOException();
        }*/

        //elements = header.split("\n")[0].split(",");
        //System.out.println(Arrays.toString(elements));

        //lineElements = readCsvLine(header);
        NamedCsvReader csvReader = NamedCsvReader.builder().build(reader);
        CloseableIterator<NamedCsvRow> iterator = csvReader.iterator();

        final CsvTableInserter inserter = new CsvTableInserter(tableName,con);

        /*final HashMap<Integer,String> columnMap = new HashMap<>();

        for (int i=0; i< lineElements.size(); i++){
            //columnMap.put(i, fixStringIfItHasQuotes(elements[i].trim()) );
            columnMap.put(i, lineElements.get(i).trim() );

        }
        Log.d(DEBUG_TAG, "Columns for the file: "+columnMap);
        boolean first = true;
        while((line = reader.readLine())!=null){
            //there is a line of data
            //elements = line.split("\n")[0].split(",");
            if(first) Log.d(DEBUG_TAG, "Element line: "+line);
            lineElements = readCsvLine(line);

            final Map<String,String> rowsMap = getColumnsAsString(lineElements.toArray(new String[0]), columnMap);
            if (first){
                Log.d(DEBUG_TAG, " in map:"+rowsMap);
                first=false;
            }
            inserter.addElement(rowsMap);
        }*/
        int c = 0;
        while (iterator.hasNext()){

            final Map<String,String> rowsMap = iterator.next().getFields();
            if (c < 1){
                Log.d(DEBUG_TAG, " in map:"+rowsMap);
                c++;
            }
            inserter.addElement(rowsMap);
        }

        //commit data
        inserter.finishInsert();
    }
    @NonNull
    private static Map<String,String> getColumnsAsString(@NonNull String[] lineElements, Map<Integer,String> colsIndices)
    {
        final HashMap<String,String>  theMap = new HashMap<>();
        for(int l=0; l<lineElements.length; l++){
            if(!colsIndices.containsKey(l))
                continue;
            //theMap.put(colsIndices.get(l), fixStringIfItHasQuotes(lineElements[l].trim()));
            theMap.put(colsIndices.get(l), lineElements[l].trim());
        }
        return theMap;
    }

    private static String fixStringIfItHasQuotes(String item) {
        if(item.length()==0){
            return item;
        }
        final String[] elements=item.split("\"");
        /*
        Log.d(DEBUG_TAG,"Splitting quotes length:"+elements.length);
        for (int i=0; i<elements.length; i++){
            Log.d(DEBUG_TAG,"Elements: "+i+" "+elements[i]);
        }
         */
        if(elements.length>1){
            //if(elements.length<3) throw new IllegalArgumentException("Malformed string");
            return elements[1];
        } else if(elements.length > 0)
            return elements[0];
        else
            return item;
    }
    //https://stackoverflow.com/questions/7800494/parse-csv-with-double-quote-in-some-cases#7800519
    public static List<String> readCsvLine(String line) throws IllegalArgumentException
    {

        List<String> list = new ArrayList<>();
        line += ",";

        for (int x = 0; x < line.length(); x++)
        {
            String s = line.substring(x);
            if (s.trim().startsWith("\""))
            {
                Matcher m = quotePattern.matcher(s);
                if (!m.find()) {
                    Log.e(DEBUG_TAG, "Cannot find pattern, "+s+" , line: "+line);
                    throw new IllegalArgumentException("CSV is malformed");
                }
                list.add(m.group(1).replace("\"\"", "\""));
                x += m.end() - 1;
            }
            else
            {
                int y = s.indexOf(",");
                if (y == -1)
                    throw new IllegalArgumentException("CSV is malformed");
                list.add(s.substring(0, y));
                x += y;
            }
        }
        return list;
    }
}
