/*
	BusTO ("backend" components)
    Copyright (C) 2016 Ludovico Pavesi

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

package it.reyboz.bustorino.middleware;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;

public class OldDB extends SQLiteOpenHelper {
    private static final String DATABASE_NAME_OLD = "bustorino.db";
    private int oldVersion = -1;

    public OldDB(Context c) {
        super(c, DATABASE_NAME_OLD, null, 1337);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        throw new IllegalStateException("Don't create this database!");
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldV, int newV) {
        // needed to determine what version this database was. Probably 8.
        this.oldVersion = oldV;
    }

    public int getOldVersion() {
        if(this.oldVersion == -1) {
            int newVersion = getReadableDatabase().getVersion();
            // getReadableDatabase might call onUpgrade
            if(this.oldVersion != -1) {
                return this.oldVersion;
            } else {
                return newVersion;
            }
        } else {
            return this.oldVersion;
        }
    }

    public static boolean doesItExist(Context context) {
        return context.getDatabasePath(DATABASE_NAME_OLD).exists();
    }

    public static boolean destroy(Context context) {
        return context.getDatabasePath(DATABASE_NAME_OLD).delete();
    }
}
