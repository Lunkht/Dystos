package dev.dystos.app.data.search;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.HashMap;
import java.util.Map;

import dev.dystos.app.domain.usecase.RecordQuizAnswer;

/**
 * Scores de quiz, en base locale.
 *
 * Table séparée de la progression : les scores sont des nombres, pas des
 * drapeaux, et les mélanger obligerait à bricoler le schéma de progression.
 */
public final class SqliteQuizScoreStore extends SQLiteOpenHelper
        implements RecordQuizAnswer.QuizScoreStore {

    private static final String DATABASE_NAME = "quiz.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE = "scores";

    public SqliteQuizScoreStore(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "quiz_id TEXT PRIMARY KEY NOT NULL, "
                + "score INTEGER NOT NULL);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE + ";");
        onCreate(db);
    }

    @Override
    public void put(String quizId, int score) {
        ContentValues values = new ContentValues(2);
        values.put("quiz_id", quizId);
        values.put("score", score);
        getWritableDatabase().replace(TABLE, null, values);
    }

    @Override
    public Integer get(String quizId) {
        Cursor cursor = getReadableDatabase().query(TABLE, new String[]{"score"},
                "quiz_id = ?", new String[]{quizId}, null, null, null, "1");
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : null;
        } finally {
            cursor.close();
        }
    }

    @Override
    public Map<String, Integer> all() {
        Map<String, Integer> scores = new HashMap<>();
        Cursor cursor = getReadableDatabase().query(TABLE,
                new String[]{"quiz_id", "score"}, null, null, null, null, "quiz_id");
        try {
            while (cursor.moveToNext()) {
                scores.put(cursor.getString(0), cursor.getInt(1));
            }
        } finally {
            cursor.close();
        }
        return scores;
    }
}