/* -*- Mode: Java; c-basic-offset: 4; tab-width: 20; indent-tabs-mode: nil; -*- */
/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.gecko.db;

import java.util.ArrayList;
import java.util.List;

import org.mozilla.gecko.R;
import org.mozilla.gecko.db.BrowserContract.Bookmarks;
import org.mozilla.gecko.db.BrowserContract.Combined;
import org.mozilla.gecko.db.BrowserContract.Favicons;
import org.mozilla.gecko.db.BrowserContract.History;
import org.mozilla.gecko.db.BrowserContract.ReadingListItems;
import org.mozilla.gecko.db.BrowserContract.SearchHistory;
import org.mozilla.gecko.db.BrowserContract.Thumbnails;
import static org.mozilla.gecko.db.DBUtils.qualifyColumn;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Build;
import android.util.Log;


final class BrowserDatabaseHelper extends SQLiteOpenHelper {

    private static final String LOGTAG = "GeckoBrowserDBHelper";
    public static final int DATABASE_VERSION = 22;
    public static final String DATABASE_NAME = "browser.db";

    final protected Context mContext;

    static final String TABLE_BOOKMARKS = Bookmarks.TABLE_NAME;
    static final String TABLE_HISTORY = History.TABLE_NAME;
    static final String TABLE_FAVICONS = Favicons.TABLE_NAME;
    static final String TABLE_THUMBNAILS = Thumbnails.TABLE_NAME;
    static final String TABLE_READING_LIST = ReadingListItems.TABLE_NAME;

    static final String VIEW_COMBINED = Combined.VIEW_NAME;
    static final String VIEW_BOOKMARKS_WITH_FAVICONS = Bookmarks.VIEW_WITH_FAVICONS;
    static final String VIEW_HISTORY_WITH_FAVICONS = History.VIEW_WITH_FAVICONS;
    static final String VIEW_COMBINED_WITH_FAVICONS = Combined.VIEW_WITH_FAVICONS;

    static final String TABLE_BOOKMARKS_JOIN_FAVICONS = TABLE_BOOKMARKS + " LEFT OUTER JOIN " +
            TABLE_FAVICONS + " ON " + qualifyColumn(TABLE_BOOKMARKS, Bookmarks.FAVICON_ID) + " = " +
            qualifyColumn(TABLE_FAVICONS, Favicons._ID);

    static final String TABLE_HISTORY_JOIN_FAVICONS = TABLE_HISTORY + " LEFT OUTER JOIN " +
            TABLE_FAVICONS + " ON " + qualifyColumn(TABLE_HISTORY, History.FAVICON_ID) + " = " +
            qualifyColumn(TABLE_FAVICONS, Favicons._ID);

    static final String TABLE_BOOKMARKS_TMP = TABLE_BOOKMARKS + "_tmp";
    static final String TABLE_HISTORY_TMP = TABLE_HISTORY + "_tmp";

    private static final String[] mobileIdColumns = new String[] { Bookmarks._ID };
    private static final String[] mobileIdSelectionArgs = new String[] { Bookmarks.MOBILE_FOLDER_GUID };

    public BrowserDatabaseHelper(Context context, String databasePath) {
        super(context, databasePath, null, DATABASE_VERSION);
        mContext = context;
    }

    private void createBookmarksTable(SQLiteDatabase db) {
        debug("Creating " + TABLE_BOOKMARKS + " table");

        db.execSQL("CREATE TABLE " + TABLE_BOOKMARKS + "(" +
                Bookmarks._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                Bookmarks.TITLE + " TEXT," +
                Bookmarks.URL + " TEXT," +
                Bookmarks.TYPE + " INTEGER NOT NULL DEFAULT " + Bookmarks.TYPE_BOOKMARK + "," +
                Bookmarks.PARENT + " INTEGER," +
                Bookmarks.POSITION + " INTEGER NOT NULL," +
                Bookmarks.KEYWORD + " TEXT," +
                Bookmarks.DESCRIPTION + " TEXT," +
                Bookmarks.TAGS + " TEXT," +
                Bookmarks.FAVICON_ID + " INTEGER," +
                Bookmarks.DATE_CREATED + " INTEGER," +
                Bookmarks.DATE_MODIFIED + " INTEGER," +
                Bookmarks.GUID + " TEXT NOT NULL," +
                Bookmarks.IS_DELETED + " INTEGER NOT NULL DEFAULT 0, " +
                "FOREIGN KEY (" + Bookmarks.PARENT + ") REFERENCES " +
                TABLE_BOOKMARKS + "(" + Bookmarks._ID + ")" +
                ");");

        db.execSQL("CREATE INDEX bookmarks_url_index ON " + TABLE_BOOKMARKS + "("
                + Bookmarks.URL + ")");
        db.execSQL("CREATE INDEX bookmarks_type_deleted_index ON " + TABLE_BOOKMARKS + "("
                + Bookmarks.TYPE + ", " + Bookmarks.IS_DELETED + ")");
        db.execSQL("CREATE UNIQUE INDEX bookmarks_guid_index ON " + TABLE_BOOKMARKS + "("
                + Bookmarks.GUID + ")");
        db.execSQL("CREATE INDEX bookmarks_modified_index ON " + TABLE_BOOKMARKS + "("
                + Bookmarks.DATE_MODIFIED + ")");
    }

    private void createHistoryTable(SQLiteDatabase db) {
        debug("Creating " + TABLE_HISTORY + " table");
        db.execSQL("CREATE TABLE " + TABLE_HISTORY + "(" +
                History._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                History.TITLE + " TEXT," +
                History.URL + " TEXT NOT NULL," +
                History.VISITS + " INTEGER NOT NULL DEFAULT 0," +
                History.FAVICON_ID + " INTEGER," +
                History.DATE_LAST_VISITED + " INTEGER," +
                History.DATE_CREATED + " INTEGER," +
                History.DATE_MODIFIED + " INTEGER," +
                History.GUID + " TEXT NOT NULL," +
                History.IS_DELETED + " INTEGER NOT NULL DEFAULT 0" +
                ");");

        db.execSQL("CREATE INDEX history_url_index ON " + TABLE_HISTORY + '('
                + History.URL + ')');
        db.execSQL("CREATE UNIQUE INDEX history_guid_index ON " + TABLE_HISTORY + '('
                + History.GUID + ')');
        db.execSQL("CREATE INDEX history_modified_index ON " + TABLE_HISTORY + '('
                + History.DATE_MODIFIED + ')');
        db.execSQL("CREATE INDEX history_visited_index ON " + TABLE_HISTORY + '('
                + History.DATE_LAST_VISITED + ')');
    }

    private void createFaviconsTable(SQLiteDatabase db) {
        debug("Creating " + TABLE_FAVICONS + " table");
        db.execSQL("CREATE TABLE " + TABLE_FAVICONS + " (" +
                Favicons._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                Favicons.URL + " TEXT UNIQUE," +
                Favicons.DATA + " BLOB," +
                Favicons.DATE_CREATED + " INTEGER," +
                Favicons.DATE_MODIFIED + " INTEGER" +
                ");");

        db.execSQL("CREATE INDEX favicons_url_index ON " + TABLE_FAVICONS + "("
                + Favicons.URL + ")");
        db.execSQL("CREATE INDEX favicons_modified_index ON " + TABLE_FAVICONS + "("
                + Favicons.DATE_MODIFIED + ")");
    }

    private void createThumbnailsTable(SQLiteDatabase db) {
        debug("Creating " + TABLE_THUMBNAILS + " table");
        db.execSQL("CREATE TABLE " + TABLE_THUMBNAILS + " (" +
                Thumbnails._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                Thumbnails.URL + " TEXT UNIQUE," +
                Thumbnails.DATA + " BLOB" +
                ");");

        db.execSQL("CREATE INDEX thumbnails_url_index ON " + TABLE_THUMBNAILS + "("
                + Thumbnails.URL + ")");
    }

    private void createBookmarksWithFaviconsView(SQLiteDatabase db) {
        debug("Creating " + VIEW_BOOKMARKS_WITH_FAVICONS + " view");

        db.execSQL("CREATE VIEW IF NOT EXISTS " + VIEW_BOOKMARKS_WITH_FAVICONS + " AS " +
                "SELECT " + qualifyColumn(TABLE_BOOKMARKS, "*") +
                ", " + qualifyColumn(TABLE_FAVICONS, Favicons.DATA) + " AS " + Bookmarks.FAVICON +
                ", " + qualifyColumn(TABLE_FAVICONS, Favicons.URL) + " AS " + Bookmarks.FAVICON_URL +
                " FROM " + TABLE_BOOKMARKS_JOIN_FAVICONS);
    }

    private void createHistoryWithFaviconsView(SQLiteDatabase db) {
        debug("Creating " + VIEW_HISTORY_WITH_FAVICONS + " view");

        db.execSQL("CREATE VIEW IF NOT EXISTS " + VIEW_HISTORY_WITH_FAVICONS + " AS " +
                "SELECT " + qualifyColumn(TABLE_HISTORY, "*") +
                ", " + qualifyColumn(TABLE_FAVICONS, Favicons.DATA) + " AS " + History.FAVICON +
                ", " + qualifyColumn(TABLE_FAVICONS, Favicons.URL) + " AS " + History.FAVICON_URL +
                " FROM " + TABLE_HISTORY_JOIN_FAVICONS);
    }

    private void createCombinedViewOn19(SQLiteDatabase db) {
        /*
        The v19 combined view removes the redundant subquery from the v16
        combined view and reorders the columns as necessary to prevent this
        from breaking any code that might be referencing columns by index.

        The rows in the ensuing view are, in order:

            Combined.BOOKMARK_ID
            Combined.HISTORY_ID
            Combined._ID (always 0)
            Combined.URL
            Combined.TITLE
            Combined.VISITS
            Combined.DISPLAY
            Combined.DATE_LAST_VISITED
            Combined.FAVICON_ID

        We need to return an _id column because CursorAdapter requires it for its
        default implementation for the getItemId() method. However, since
        we're not using this feature in the parts of the UI using this view,
        we can just use 0 for all rows.
         */

        db.execSQL("CREATE VIEW IF NOT EXISTS " + VIEW_COMBINED + " AS" +

                // Bookmarks without history.
                " SELECT " + qualifyColumn(TABLE_BOOKMARKS, Bookmarks._ID) + " AS " + Combined.BOOKMARK_ID + "," +
                    "-1 AS " + Combined.HISTORY_ID + "," +
                    "0 AS " + Combined._ID + "," +
                    qualifyColumn(TABLE_BOOKMARKS, Bookmarks.URL) + " AS " + Combined.URL + ", " +
                    qualifyColumn(TABLE_BOOKMARKS, Bookmarks.TITLE) + " AS " + Combined.TITLE + ", " +
                    "-1 AS " + Combined.VISITS + ", " +
                    "-1 AS " + Combined.DATE_LAST_VISITED + "," +
                    qualifyColumn(TABLE_BOOKMARKS, Bookmarks.FAVICON_ID) + " AS " + Combined.FAVICON_ID +
                " FROM " + TABLE_BOOKMARKS +
                " WHERE " +
                    qualifyColumn(TABLE_BOOKMARKS, Bookmarks.TYPE)  + " = " + Bookmarks.TYPE_BOOKMARK + " AND " +
                    // Ignore pinned bookmarks.
                    qualifyColumn(TABLE_BOOKMARKS, Bookmarks.PARENT)  + " <> " + Bookmarks.FIXED_PINNED_LIST_ID + " AND " +
                    qualifyColumn(TABLE_BOOKMARKS, Bookmarks.IS_DELETED)  + " = 0 AND " +
                    qualifyColumn(TABLE_BOOKMARKS, Bookmarks.URL) +
                        " NOT IN (SELECT " + History.URL + " FROM " + TABLE_HISTORY + ")" +
                " UNION ALL" +

                    // History with and without bookmark.
                    " SELECT " +
                        "CASE " + qualifyColumn(TABLE_BOOKMARKS, Bookmarks.IS_DELETED) +

                            // Give pinned bookmarks a NULL ID so that they're not treated as bookmarks. We can't
                            // completely ignore them here because they're joined with history entries we care about.
                            " WHEN 0 THEN " +
                                "CASE " + qualifyColumn(TABLE_BOOKMARKS, Bookmarks.PARENT) +
                                    " WHEN " + Bookmarks.FIXED_PINNED_LIST_ID + " THEN " +
                                        "NULL " +
                                    "ELSE " +
                                        qualifyColumn(TABLE_BOOKMARKS, Bookmarks._ID) +
                                " END " +
                            "ELSE " +
                                "NULL " +
                        "END AS " + Combined.BOOKMARK_ID + "," +
                        qualifyColumn(TABLE_HISTORY, History._ID) + " AS " + Combined.HISTORY_ID + "," +
                        "0 AS " + Combined._ID + "," +
                        qualifyColumn(TABLE_HISTORY, History.URL) + " AS " + Combined.URL + "," +

                        // Prioritize bookmark titles over history titles, since the user may have
                        // customized the title for a bookmark.
                        "COALESCE(" + qualifyColumn(TABLE_BOOKMARKS, Bookmarks.TITLE) + ", " +
                                      qualifyColumn(TABLE_HISTORY, History.TITLE) +
                                ") AS " + Combined.TITLE + "," +
                        qualifyColumn(TABLE_HISTORY, History.VISITS) + " AS " + Combined.VISITS + "," +
                        qualifyColumn(TABLE_HISTORY, History.DATE_LAST_VISITED) + " AS " + Combined.DATE_LAST_VISITED + "," +
                        qualifyColumn(TABLE_HISTORY, History.FAVICON_ID) + " AS " + Combined.FAVICON_ID +

                    // We really shouldn't be selecting deleted bookmarks, but oh well.
                    " FROM " + TABLE_HISTORY + " LEFT OUTER JOIN " + TABLE_BOOKMARKS +
                    " ON " + qualifyColumn(TABLE_BOOKMARKS, Bookmarks.URL) + " = " + qualifyColumn(TABLE_HISTORY, History.URL) +
                    " WHERE " +
                        qualifyColumn(TABLE_HISTORY, History.IS_DELETED) + " = 0 AND " +
                        "(" +
                            // The left outer join didn't match...
                            qualifyColumn(TABLE_BOOKMARKS, Bookmarks.TYPE) + " IS NULL OR " +

                            // ... or it's a bookmark. This is less efficient than filtering prior
                            // to the join if you have lots of folders.
                            qualifyColumn(TABLE_BOOKMARKS, Bookmarks.TYPE) + " = " + Bookmarks.TYPE_BOOKMARK +
                        ")"
        );

        debug("Creating " + VIEW_COMBINED_WITH_FAVICONS + " view");

        db.execSQL("CREATE VIEW IF NOT EXISTS " + VIEW_COMBINED_WITH_FAVICONS + " AS" +
                " SELECT " + qualifyColumn(VIEW_COMBINED, "*") + ", " +
                    qualifyColumn(TABLE_FAVICONS, Favicons.URL) + " AS " + Combined.FAVICON_URL + ", " +
                    qualifyColumn(TABLE_FAVICONS, Favicons.DATA) + " AS " + Combined.FAVICON +
                " FROM " + VIEW_COMBINED + " LEFT OUTER JOIN " + TABLE_FAVICONS +
                    " ON " + Combined.FAVICON_ID + " = " + qualifyColumn(TABLE_FAVICONS, Favicons._ID));

    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        debug("Creating browser.db: " + db.getPath());

        for (Table table : BrowserProvider.sTables) {
            table.onCreate(db);
        }

        createBookmarksTable(db);
        createHistoryTable(db);
        createFaviconsTable(db);
        createThumbnailsTable(db);

        createBookmarksWithFaviconsView(db);
        createHistoryWithFaviconsView(db);
        createCombinedViewOn19(db);

        createOrUpdateSpecialFolder(db, Bookmarks.PLACES_FOLDER_GUID,
            R.string.bookmarks_folder_places, 0);

        createOrUpdateAllSpecialFolders(db);
        createReadingListTable(db);
        createSearchHistoryTable(db);
    }

    private void createSearchHistoryTable(SQLiteDatabase db) {
        debug("Creating " + SearchHistory.TABLE_NAME + " table");

        db.execSQL("CREATE TABLE " + SearchHistory.TABLE_NAME + "(" +
                    SearchHistory._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    SearchHistory.QUERY + " TEXT UNIQUE NOT NULL, " +
                    SearchHistory.DATE_LAST_VISITED + " INTEGER, " +
                    SearchHistory.VISITS + " INTEGER ) ");

        db.execSQL("CREATE INDEX idx_search_history_last_visited ON " +
                SearchHistory.TABLE_NAME + "(" + SearchHistory.DATE_LAST_VISITED + ")");
    }

    private void createReadingListTable(SQLiteDatabase db) {
        debug("Creating " + TABLE_READING_LIST + " table");

        db.execSQL("CREATE TABLE " + TABLE_READING_LIST + "(" +
                    ReadingListItems._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    ReadingListItems.URL + " TEXT NOT NULL, " +
                    ReadingListItems.TITLE + " TEXT, " +
                    ReadingListItems.EXCERPT + " TEXT, " +
                    ReadingListItems.READ + " TINYINT DEFAULT 0, " +
                    ReadingListItems.IS_DELETED + " TINYINT DEFAULT 0, " +
                    ReadingListItems.GUID + " TEXT UNIQUE NOT NULL, " +
                    ReadingListItems.DATE_MODIFIED + " INTEGER NOT NULL, " +
                    ReadingListItems.DATE_CREATED + " INTEGER NOT NULL, " +
                    ReadingListItems.LENGTH + " INTEGER DEFAULT 0, " +
                    ReadingListItems.CONTENT_STATUS + " TINYINT DEFAULT " + ReadingListItems.STATUS_UNFETCHED + "); ");

        db.execSQL("CREATE INDEX reading_list_url ON " + TABLE_READING_LIST + "("
                + ReadingListItems.URL + ")");
        db.execSQL("CREATE UNIQUE INDEX reading_list_guid ON " + TABLE_READING_LIST + "("
                + ReadingListItems.GUID + ")");
        db.execSQL("CREATE INDEX reading_list_content_status ON " + TABLE_READING_LIST + "("
                + ReadingListItems.CONTENT_STATUS + ")");
    }

    private void createOrUpdateAllSpecialFolders(SQLiteDatabase db) {
        createOrUpdateSpecialFolder(db, Bookmarks.MOBILE_FOLDER_GUID,
            R.string.bookmarks_folder_mobile, 0);
        createOrUpdateSpecialFolder(db, Bookmarks.TOOLBAR_FOLDER_GUID,
            R.string.bookmarks_folder_toolbar, 1);
        createOrUpdateSpecialFolder(db, Bookmarks.MENU_FOLDER_GUID,
            R.string.bookmarks_folder_menu, 2);
        createOrUpdateSpecialFolder(db, Bookmarks.TAGS_FOLDER_GUID,
            R.string.bookmarks_folder_tags, 3);
        createOrUpdateSpecialFolder(db, Bookmarks.UNFILED_FOLDER_GUID,
            R.string.bookmarks_folder_unfiled, 4);
        createOrUpdateSpecialFolder(db, Bookmarks.PINNED_FOLDER_GUID,
            R.string.bookmarks_folder_pinned, 5);
    }

    private void createOrUpdateSpecialFolder(SQLiteDatabase db,
            String guid, int titleId, int position) {
        ContentValues values = new ContentValues();
        values.put(Bookmarks.GUID, guid);
        values.put(Bookmarks.TYPE, Bookmarks.TYPE_FOLDER);
        values.put(Bookmarks.POSITION, position);

        if (guid.equals(Bookmarks.PLACES_FOLDER_GUID)) {
            values.put(Bookmarks._ID, Bookmarks.FIXED_ROOT_ID);
        } else if (guid.equals(Bookmarks.PINNED_FOLDER_GUID)) {
            values.put(Bookmarks._ID, Bookmarks.FIXED_PINNED_LIST_ID);
        }

        // Set the parent to 0, which sync assumes is the root
        values.put(Bookmarks.PARENT, Bookmarks.FIXED_ROOT_ID);

        String title = mContext.getResources().getString(titleId);
        values.put(Bookmarks.TITLE, title);

        long now = System.currentTimeMillis();
        values.put(Bookmarks.DATE_CREATED, now);
        values.put(Bookmarks.DATE_MODIFIED, now);

        int updated = db.update(TABLE_BOOKMARKS, values,
                                Bookmarks.GUID + " = ?",
                                new String[] { guid });

        if (updated == 0) {
            db.insert(TABLE_BOOKMARKS, Bookmarks.GUID, values);
            debug("Inserted special folder: " + guid);
        } else {
            debug("Updated special folder: " + guid);
        }
    }

    private boolean isSpecialFolder(ContentValues values) {
        String guid = values.getAsString(Bookmarks.GUID);
        if (guid == null) {
            return false;
        }

        return guid.equals(Bookmarks.MOBILE_FOLDER_GUID) ||
               guid.equals(Bookmarks.MENU_FOLDER_GUID) ||
               guid.equals(Bookmarks.TOOLBAR_FOLDER_GUID) ||
               guid.equals(Bookmarks.UNFILED_FOLDER_GUID) ||
               guid.equals(Bookmarks.TAGS_FOLDER_GUID);
    }

    private void migrateBookmarkFolder(SQLiteDatabase db, int folderId,
            BookmarkMigrator migrator) {
        Cursor c = null;

        debug("Migrating bookmark folder with id = " + folderId);

        String selection = Bookmarks.PARENT + " = " + folderId;
        String[] selectionArgs = null;

        boolean isRootFolder = (folderId == Bookmarks.FIXED_ROOT_ID);

        // If we're loading the root folder, we have to account for
        // any previously created special folder that was created without
        // setting a parent id (e.g. mobile folder) and making sure we're
        // not adding any infinite recursion as root's parent is root itself.
        if (isRootFolder) {
            selection = Bookmarks.GUID + " != ?" + " AND (" +
                        selection + " OR " + Bookmarks.PARENT + " = NULL)";
            selectionArgs = new String[] { Bookmarks.PLACES_FOLDER_GUID };
        }

        List<Integer> subFolders = new ArrayList<Integer>();
        List<ContentValues> invalidSpecialEntries = new ArrayList<ContentValues>();

        try {
            c = db.query(TABLE_BOOKMARKS_TMP,
                         null,
                         selection,
                         selectionArgs,
                         null, null, null);

            // The key point here is that bookmarks should be added in
            // parent order to avoid any problems with the foreign key
            // in Bookmarks.PARENT.
            while (c.moveToNext()) {
                ContentValues values = new ContentValues();

                // We're using a null projection in the query which
                // means we're getting all columns from the table.
                // It's safe to simply transform the row into the
                // values to be inserted on the new table.
                DatabaseUtils.cursorRowToContentValues(c, values);

                boolean isSpecialFolder = isSpecialFolder(values);

                // The mobile folder used to be created with PARENT = NULL.
                // We want fix that here.
                if (values.getAsLong(Bookmarks.PARENT) == null && isSpecialFolder)
                    values.put(Bookmarks.PARENT, Bookmarks.FIXED_ROOT_ID);

                if (isRootFolder && !isSpecialFolder) {
                    invalidSpecialEntries.add(values);
                    continue;
                }

                if (migrator != null)
                    migrator.updateForNewTable(values);

                debug("Migrating bookmark: " + values.getAsString(Bookmarks.TITLE));
                db.insert(TABLE_BOOKMARKS, Bookmarks.URL, values);

                Integer type = values.getAsInteger(Bookmarks.TYPE);
                if (type != null && type == Bookmarks.TYPE_FOLDER)
                    subFolders.add(values.getAsInteger(Bookmarks._ID));
            }
        } finally {
            if (c != null)
                c.close();
        }

        // At this point is safe to assume that the mobile folder is
        // in the new table given that we've always created it on
        // database creation time.
        final int nInvalidSpecialEntries = invalidSpecialEntries.size();
        if (nInvalidSpecialEntries > 0) {
            Integer mobileFolderId = getMobileFolderId(db);
            if (mobileFolderId == null) {
                Log.e(LOGTAG, "Error migrating invalid special folder entries: mobile folder id is null");
                return;
            }

            debug("Found " + nInvalidSpecialEntries + " invalid special folder entries");
            for (int i = 0; i < nInvalidSpecialEntries; i++) {
                ContentValues values = invalidSpecialEntries.get(i);
                values.put(Bookmarks.PARENT, mobileFolderId);

                db.insert(TABLE_BOOKMARKS, Bookmarks.URL, values);
            }
        }

        final int nSubFolders = subFolders.size();
        for (int i = 0; i < nSubFolders; i++) {
            int subFolderId = subFolders.get(i);
            migrateBookmarkFolder(db, subFolderId, migrator);
        }
    }

    private void migrateBookmarksTable(SQLiteDatabase db) {
        migrateBookmarksTable(db, null);
    }

    private void migrateBookmarksTable(SQLiteDatabase db, BookmarkMigrator migrator) {
        debug("Renaming bookmarks table to " + TABLE_BOOKMARKS_TMP);
        db.execSQL("ALTER TABLE " + TABLE_BOOKMARKS +
                   " RENAME TO " + TABLE_BOOKMARKS_TMP);

        debug("Dropping views and indexes related to " + TABLE_BOOKMARKS);

        db.execSQL("DROP INDEX IF EXISTS bookmarks_url_index");
        db.execSQL("DROP INDEX IF EXISTS bookmarks_type_deleted_index");
        db.execSQL("DROP INDEX IF EXISTS bookmarks_guid_index");
        db.execSQL("DROP INDEX IF EXISTS bookmarks_modified_index");

        createBookmarksTable(db);

        createOrUpdateSpecialFolder(db, Bookmarks.PLACES_FOLDER_GUID,
            R.string.bookmarks_folder_places, 0);

        migrateBookmarkFolder(db, Bookmarks.FIXED_ROOT_ID, migrator);

        // Ensure all special folders exist and have the
        // right folder hierarchy.
        createOrUpdateAllSpecialFolders(db);

        debug("Dropping bookmarks temporary table");
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BOOKMARKS_TMP);
    }

    /**
     * Migrate a history table from some old version to the newest one by creating the new table and
     * copying all the data over.
     */
    private void migrateHistoryTable(SQLiteDatabase db) {
        debug("Renaming history table to " + TABLE_HISTORY_TMP);
        db.execSQL("ALTER TABLE " + TABLE_HISTORY +
                   " RENAME TO " + TABLE_HISTORY_TMP);

        debug("Dropping views and indexes related to " + TABLE_HISTORY);

        db.execSQL("DROP INDEX IF EXISTS history_url_index");
        db.execSQL("DROP INDEX IF EXISTS history_guid_index");
        db.execSQL("DROP INDEX IF EXISTS history_modified_index");
        db.execSQL("DROP INDEX IF EXISTS history_visited_index");

        createHistoryTable(db);

        db.execSQL("INSERT INTO " + TABLE_HISTORY + " SELECT * FROM " + TABLE_HISTORY_TMP);

        debug("Dropping history temporary table");
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY_TMP);
    }

    private void upgradeDatabaseFrom3to4(SQLiteDatabase db) {
        migrateBookmarksTable(db, new BookmarkMigrator3to4());
    }

    private void upgradeDatabaseFrom6to7(SQLiteDatabase db) {
        debug("Removing history visits with NULL GUIDs");
        db.execSQL("DELETE FROM " + TABLE_HISTORY + " WHERE " + History.GUID + " IS NULL");

        migrateBookmarksTable(db);
        migrateHistoryTable(db);
    }

    private void upgradeDatabaseFrom7to8(SQLiteDatabase db) {
        debug("Combining history entries with the same URL");

        final String TABLE_DUPES = "duped_urls";
        final String TOTAL = "total";
        final String LATEST = "latest";
        final String WINNER = "winner";

        db.execSQL("CREATE TEMP TABLE " + TABLE_DUPES + " AS" +
                  " SELECT " + History.URL + ", " +
                              "SUM(" + History.VISITS + ") AS " + TOTAL + ", " +
                              "MAX(" + History.DATE_MODIFIED + ") AS " + LATEST + ", " +
                              "MAX(" + History._ID + ") AS " + WINNER +
                  " FROM " + TABLE_HISTORY +
                  " GROUP BY " + History.URL +
                  " HAVING count(" + History.URL + ") > 1");

        db.execSQL("CREATE UNIQUE INDEX " + TABLE_DUPES + "_url_index ON " +
                   TABLE_DUPES + " (" + History.URL + ")");

        final String fromClause = " FROM " + TABLE_DUPES + " WHERE " +
                                  qualifyColumn(TABLE_DUPES, History.URL) + " = " +
                                  qualifyColumn(TABLE_HISTORY, History.URL);

        db.execSQL("UPDATE " + TABLE_HISTORY +
                  " SET " + History.VISITS + " = (SELECT " + TOTAL + fromClause + "), " +
                            History.DATE_MODIFIED + " = (SELECT " + LATEST + fromClause + "), " +
                            History.IS_DELETED + " = " +
                                "(" + History._ID + " <> (SELECT " + WINNER + fromClause + "))" +
                  " WHERE " + History.URL + " IN (SELECT " + History.URL + " FROM " + TABLE_DUPES + ")");

        db.execSQL("DROP TABLE " + TABLE_DUPES);
    }

    private void upgradeDatabaseFrom10to11(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX bookmarks_type_deleted_index ON " + TABLE_BOOKMARKS + "("
                + Bookmarks.TYPE + ", " + Bookmarks.IS_DELETED + ")");
    }

    private void upgradeDatabaseFrom12to13(SQLiteDatabase db) {
        createFaviconsTable(db);

        // Add favicon_id column to the history/bookmarks tables. We wrap this in a try-catch
        // because the column *may* already exist at this point (depending on how many upgrade
        // steps have been performed in this operation). In which case these queries will throw,
        // but we don't care.
        try {
            db.execSQL("ALTER TABLE " + TABLE_HISTORY +
                                    " ADD COLUMN " + History.FAVICON_ID + " INTEGER");
            db.execSQL("ALTER TABLE " + TABLE_BOOKMARKS +
                               " ADD COLUMN " + Bookmarks.FAVICON_ID + " INTEGER");
        } catch (SQLException e) {
            // Don't care.
            debug("Exception adding favicon_id column. We're probably fine." + e);
        }

        createThumbnailsTable(db);

        db.execSQL("DROP VIEW IF EXISTS bookmarks_with_images");
        db.execSQL("DROP VIEW IF EXISTS history_with_images");
        db.execSQL("DROP VIEW IF EXISTS combined_with_images");

        createBookmarksWithFaviconsView(db);
        createHistoryWithFaviconsView(db);

        db.execSQL("DROP TABLE IF EXISTS images");
    }

    private void upgradeDatabaseFrom13to14(SQLiteDatabase db) {
        createOrUpdateSpecialFolder(db, Bookmarks.PINNED_FOLDER_GUID,
            R.string.bookmarks_folder_pinned, 6);
    }

    private void upgradeDatabaseFrom14to15(SQLiteDatabase db) {
        Cursor c = null;
        try {
            // Get all the pinned bookmarks
            c = db.query(TABLE_BOOKMARKS,
                         new String[] { Bookmarks._ID, Bookmarks.URL },
                         Bookmarks.PARENT + " = ?",
                         new String[] { Integer.toString(Bookmarks.FIXED_PINNED_LIST_ID) },
                         null, null, null);

            while (c.moveToNext()) {
                // Check if this URL can be parsed as a URI with a valid scheme.
                String url = c.getString(c.getColumnIndexOrThrow(Bookmarks.URL));
                if (Uri.parse(url).getScheme() != null) {
                    continue;
                }

                // If it can't, update the URL to be an encoded "user-entered" value.
                ContentValues values = new ContentValues(1);
                String newUrl = Uri.fromParts("user-entered", url, null).toString();
                values.put(Bookmarks.URL, newUrl);
                db.update(TABLE_BOOKMARKS, values, Bookmarks._ID + " = ?",
                          new String[] { Integer.toString(c.getInt(c.getColumnIndexOrThrow(Bookmarks._ID))) });
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    private void upgradeDatabaseFrom15to16(SQLiteDatabase db) {
        // No harm in creating the v19 combined view here: means we don't need two almost-identical
        // functions to define both the v16 and v19 ones. The upgrade path will redundantly drop
        // and recreate the view again. *shrug*
        createV19CombinedView(db);
    }

    private void upgradeDatabaseFrom16to17(SQLiteDatabase db) {
        // Purge any 0-byte favicons/thumbnails
        try {
            db.execSQL("DELETE FROM " + TABLE_FAVICONS +
                    " WHERE length(" + Favicons.DATA + ") = 0");
            db.execSQL("DELETE FROM " + TABLE_THUMBNAILS +
                    " WHERE length(" + Thumbnails.DATA + ") = 0");
        } catch (SQLException e) {
            Log.e(LOGTAG, "Error purging invalid favicons or thumbnails", e);
        }
    }

    /*
     * Moves reading list items from 'bookmarks' table to 'reading_list' table. Uses the
     * same item GUID.
     */
    private void upgradeDatabaseFrom17to18(SQLiteDatabase db) {
        debug("Moving reading list items from 'bookmarks' table to 'reading_list' table");

        final String selection = Bookmarks.PARENT + " = ? AND " + Bookmarks.IS_DELETED + " = ? ";
        final String[] selectionArgs = { String.valueOf(Bookmarks.FIXED_READING_LIST_ID), "0" };
        final String[] projection = {   Bookmarks._ID,
                                        Bookmarks.GUID,
                                        Bookmarks.URL,
                                        Bookmarks.DATE_MODIFIED,
                                        Bookmarks.DATE_CREATED,
                                        Bookmarks.TITLE };
        Cursor cursor = null;
        try {
            // Start transaction
            db.beginTransaction();

            // Create 'reading_list' table
            createReadingListTable(db);

            // Get all the reading list items from bookmarks table
            cursor = db.query(TABLE_BOOKMARKS, projection, selection, selectionArgs,
                         null, null, null);

            // Insert reading list items into reading_list table
            while (cursor.moveToNext()) {
                debug(DatabaseUtils.dumpCurrentRowToString(cursor));
                ContentValues values = new ContentValues();
                DatabaseUtils.cursorStringToContentValues(cursor, Bookmarks.URL, values, ReadingListItems.URL);
                DatabaseUtils.cursorStringToContentValues(cursor, Bookmarks.GUID, values, ReadingListItems.GUID);
                DatabaseUtils.cursorStringToContentValues(cursor, Bookmarks.TITLE, values, ReadingListItems.TITLE);
                DatabaseUtils.cursorLongToContentValues(cursor, Bookmarks.DATE_CREATED, values, ReadingListItems.DATE_CREATED);
                DatabaseUtils.cursorLongToContentValues(cursor, Bookmarks.DATE_MODIFIED, values, ReadingListItems.DATE_MODIFIED);

                db.insertOrThrow(TABLE_READING_LIST, null, values);
            }

            // Delete reading list items from bookmarks table
            db.delete(TABLE_BOOKMARKS,
                      Bookmarks.PARENT + " = ? ",
                      new String[] { String.valueOf(Bookmarks.FIXED_READING_LIST_ID) });

            // Delete reading list special folder
            db.delete(TABLE_BOOKMARKS,
                      Bookmarks._ID + " = ? ",
                      new String[] { String.valueOf(Bookmarks.FIXED_READING_LIST_ID) });
            // Done
            db.setTransactionSuccessful();

        } catch (SQLException e) {
            Log.e(LOGTAG, "Error migrating reading list items", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.endTransaction();
        }
    }

    private void upgradeDatabaseFrom18to19(SQLiteDatabase db) {
        // Redefine the "combined" view...
        createV19CombinedView(db);

        // Kill any history entries with NULL URL. This ostensibly can't happen...
        db.execSQL("DELETE FROM " + TABLE_HISTORY + " WHERE " + History.URL + " IS NULL");

        // Similar for bookmark types. Replaces logic from the combined view, also shouldn't happen.
        db.execSQL("UPDATE " + TABLE_BOOKMARKS + " SET " +
                   Bookmarks.TYPE + " = " + Bookmarks.TYPE_BOOKMARK +
                   " WHERE " + Bookmarks.TYPE + " IS NULL");
    }

    private void upgradeDatabaseFrom19to20(SQLiteDatabase db) {
        createSearchHistoryTable(db);
    }

    private void upgradeDatabaseFrom21to22(SQLiteDatabase db) {
        debug("Adding CONTENT_STATUS column to reading list table.");

        db.execSQL("ALTER TABLE " + TABLE_READING_LIST +
                   " ADD COLUMN " + ReadingListItems.CONTENT_STATUS +
                   " TINYINT DEFAULT " + ReadingListItems.STATUS_UNFETCHED);

        db.execSQL("CREATE INDEX reading_list_content_status ON " + TABLE_READING_LIST + "("
                + ReadingListItems.CONTENT_STATUS + ")");
    }

    private void createV19CombinedView(SQLiteDatabase db) {
        db.execSQL("DROP VIEW IF EXISTS " + VIEW_COMBINED);
        db.execSQL("DROP VIEW IF EXISTS " + VIEW_COMBINED_WITH_FAVICONS);

        createCombinedViewOn19(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        debug("Upgrading browser.db: " + db.getPath() + " from " +
                oldVersion + " to " + newVersion);

        // We have to do incremental upgrades until we reach the current
        // database schema version.
        for (int v = oldVersion + 1; v <= newVersion; v++) {
            switch(v) {
                case 4:
                    upgradeDatabaseFrom3to4(db);
                    break;

                case 7:
                    upgradeDatabaseFrom6to7(db);
                    break;

                case 8:
                    upgradeDatabaseFrom7to8(db);
                    break;

                case 11:
                    upgradeDatabaseFrom10to11(db);
                    break;

                case 13:
                    upgradeDatabaseFrom12to13(db);
                    break;

                case 14:
                    upgradeDatabaseFrom13to14(db);
                    break;

                case 15:
                    upgradeDatabaseFrom14to15(db);
                    break;

                case 16:
                    upgradeDatabaseFrom15to16(db);
                    break;

                case 17:
                    upgradeDatabaseFrom16to17(db);
                    break;

                case 18:
                    upgradeDatabaseFrom17to18(db);
                    break;

                case 19:
                    upgradeDatabaseFrom18to19(db);
                    break;

                case 20:
                    upgradeDatabaseFrom19to20(db);
                    break;

                case 22:
                    upgradeDatabaseFrom21to22(db);
                    break;
            }
        }

        for (Table table : BrowserProvider.sTables) {
            table.onUpgrade(db, oldVersion, newVersion);
        }

        // Delete the obsolete favicon database after all other upgrades complete.
        // This can probably equivalently be moved into upgradeDatabaseFrom12to13.
        if (oldVersion < 13 && newVersion >= 13) {
            if (mContext.getDatabasePath("favicon_urls.db").exists()) {
                mContext.deleteDatabase("favicon_urls.db");
            }
        }
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        debug("Opening browser.db: " + db.getPath());

        Cursor cursor = null;
        try {
            cursor = db.rawQuery("PRAGMA foreign_keys=ON", null);
        } finally {
            if (cursor != null)
                cursor.close();
        }
        cursor = null;
        try {
            cursor = db.rawQuery("PRAGMA synchronous=NORMAL", null);
        } finally {
            if (cursor != null)
                cursor.close();
        }

        // From Honeycomb on, it's possible to run several db
        // commands in parallel using multiple connections.
        if (Build.VERSION.SDK_INT >= 11) {
            // Modern Android allows WAL to be enabled through a mode flag.
            if (Build.VERSION.SDK_INT < 16) {
                db.enableWriteAheadLogging();

                // This does nothing on 16+.
                db.setLockingEnabled(false);
            }
        } else {
            // Pre-Honeycomb, we can do some lesser optimizations.
            cursor = null;
            try {
                cursor = db.rawQuery("PRAGMA journal_mode=PERSIST", null);
            } finally {
                if (cursor != null)
                    cursor.close();
            }
        }
    }

    // Calculate these once, at initialization. isLoggable is too expensive to
    // have in-line in each log call.
    private static final boolean logDebug   = Log.isLoggable(LOGTAG, Log.DEBUG);
    private static final boolean logVerbose = Log.isLoggable(LOGTAG, Log.VERBOSE);
    protected static void trace(String message) {
        if (logVerbose) {
            Log.v(LOGTAG, message);
        }
    }

    protected static void debug(String message) {
        if (logDebug) {
            Log.d(LOGTAG, message);
        }
    }

    private Integer getMobileFolderId(SQLiteDatabase db) {
        Cursor c = null;

        try {
            c = db.query(TABLE_BOOKMARKS,
                         mobileIdColumns,
                         Bookmarks.GUID + " = ?",
                         mobileIdSelectionArgs,
                         null, null, null);

            if (c == null || !c.moveToFirst())
                return null;

            return c.getInt(c.getColumnIndex(Bookmarks._ID));
        } finally {
            if (c != null)
                c.close();
        }
    }

    private interface BookmarkMigrator {
        public void updateForNewTable(ContentValues bookmark);
    }

    private class BookmarkMigrator3to4 implements BookmarkMigrator {
        @Override
        public void updateForNewTable(ContentValues bookmark) {
            Integer isFolder = bookmark.getAsInteger("folder");
            if (isFolder == null || isFolder != 1) {
                bookmark.put(Bookmarks.TYPE, Bookmarks.TYPE_BOOKMARK);
            } else {
                bookmark.put(Bookmarks.TYPE, Bookmarks.TYPE_FOLDER);
            }

            bookmark.remove("folder");
        }
    }
}

