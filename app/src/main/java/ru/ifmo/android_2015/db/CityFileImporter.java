package ru.ifmo.android_2015.db;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import ru.ifmo.android_2015.json.CityJsonParser;
import ru.ifmo.android_2015.json.CityParserCallback;
import ru.ifmo.android_2015.util.ObservableInputStream;
import ru.ifmo.android_2015.util.ProgressCallback;

public abstract class CityFileImporter implements CityParserCallback {

    private SQLiteDatabase db;
    private int importedCount;

    private static final String REQUEST = "INSERT INTO " +
            CityContract.Cities.TABLE +
            "(" +
            CityContract.Cities.CITY_ID +
            "," +
            CityContract.Cities.NAME +
            "," +
            CityContract.Cities.COUNTRY +
            "," +
            CityContract.Cities.LATITUDE +
            "," +
            CityContract.Cities.LONGITUDE +
            ")" +
            " VALUES(?, ?, ?, ?, ?)";

    private final SQLiteStatement query;

    public CityFileImporter(SQLiteDatabase db) {
        this.db = db;
        query = db.compileStatement(REQUEST);
    }

    public final synchronized void importCities(File srcFile,
                                                ProgressCallback progressCallback)
            throws IOException {

        InputStream in = null;

        try {
            long fileSize = srcFile.length();
            in = new FileInputStream(srcFile);
            in = new BufferedInputStream(in);
            in = new ObservableInputStream(in, fileSize, progressCallback);
            in = new GZIPInputStream(in);
            importCities(in);

        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Failed to close file: " + e, e);
                }
            }
        }
    }

    protected abstract CityJsonParser createParser();

    //Добавена явная транзакция
    private void importCities(InputStream in) {
        CityJsonParser parser = createParser();
        try {
            db.beginTransaction();
            parser.parseCities(in, this);
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to parse cities: " + e, e);
        } finally {
            if (query != null) {
                try {
                    query.close();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Failed to close SQLiteStatement: " + e, e);
                }
            }
            db.endTransaction();
        }
    }

    @Override
    public void onCityParsed(long id, String name, String country, double lat, double lon) {
        insertCity(db, id, name, country, lat, lon);
        importedCount++;
        if (importedCount % 1000 == 0) {
            Log.d(LOG_TAG, "Processed " + importedCount + " cities");
        }
    }

    // Добавлено SQLiteStatement
    private boolean insertCity(SQLiteDatabase db,
                               long id,
                               @NonNull String name,
                               @NonNull String country,
                               double latitude,
                               double longitude) {
        query.bindLong(1, id);
        query.bindString(2, name);
        query.bindString(3, country);
        query.bindDouble(4, latitude);
        query.bindDouble(5, longitude);

        long rowId = query.executeInsert();

        if (rowId < 0) {
            Log.w(LOG_TAG, "Failed to insert city: id=" + id + " name=" + name);
            return false;
        }
        return true;
    }

    private static final String LOG_TAG = "CityReader";

}
