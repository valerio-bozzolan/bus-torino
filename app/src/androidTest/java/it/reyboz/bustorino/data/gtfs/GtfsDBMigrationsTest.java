package it.reyboz.bustorino.data.gtfs;

import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import it.reyboz.bustorino.data.gtfs.GtfsDatabase;

//@RunWith(AndroidJUnit4.class)
public class GtfsDBMigrationsTest {
    private static final String TEST_DB = "migration-test";

    @Rule
    public MigrationTestHelper helper;

    public GtfsDBMigrationsTest() {
        helper = new MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
                GtfsDatabase.class.getCanonicalName(),
                new FrameworkSQLiteOpenHelperFactory());
    }

    @Test
    public void migrateAll() throws IOException {
        // Create earliest version of the database.
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 1);
        db.close();

        // Open latest version of the database. Room will validate the schema
        // once all migrations execute.
        GtfsDatabase appDb = Room.databaseBuilder(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                GtfsDatabase.class,
                TEST_DB)
                .addMigrations(ALL_MIGRATIONS).build();
        appDb.getOpenHelper().getWritableDatabase();
        appDb.close();
    }

    // Array of all migrations
    private static final Migration[] ALL_MIGRATIONS = new Migration[]{
            GtfsDatabase.Companion.getMIGRATION_1_2(),
    };
}

