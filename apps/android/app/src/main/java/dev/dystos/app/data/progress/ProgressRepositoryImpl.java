package dev.dystos.app.data.progress;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.dystos.app.domain.model.Progress;
import dev.dystos.app.domain.repository.ProgressRepository;

/**
 * Progression locale, en SQLite.
 *
 * SQLite est utilisé directement : une seule table de paires
 * (leçon, état) suffit, et cela évite un générateur de code au build. La table
 * d'index plein texte de la recherche viendra dans le même fichier.
 */
public final class ProgressRepositoryImpl implements ProgressRepository {

    private final ProgressDatabase database;

    public ProgressRepositoryImpl(Context context) {
        this.database = new ProgressDatabase(context.getApplicationContext());
    }

    @Override
    public Progress get() {
        Map<String, Boolean> lessonsRead = new LinkedHashMap<>();
        Map<String, Boolean> exercisesSolved = new LinkedHashMap<>();

        SQLiteDatabase db = database.getReadableDatabase();
        try (Cursor cursor = db.query(
                ProgressDatabase.TABLE_PROGRESS,
                new String[] {
                    ProgressDatabase.COLUMN_LESSON_ID,
                    ProgressDatabase.COLUMN_LESSON_READ,
                    ProgressDatabase.COLUMN_EXERCISE_SOLVED
                },
                null,
                null,
                null,
                null,
                ProgressDatabase.COLUMN_LESSON_ID + " ASC")) {
            while (cursor.moveToNext()) {
                String lessonId = cursor.getString(0);
                lessonsRead.put(lessonId, cursor.getInt(1) == 1);
                exercisesSolved.put(lessonId, cursor.getInt(2) == 1);
            }
        }
        return new Progress(lessonsRead, exercisesSolved);
    }

    @Override
    public void markLessonRead(String lessonId) {
        if (lessonId == null || lessonId.isEmpty()) {
            return;
        }
        database.update(lessonId, true, false);
    }

    @Override
    public void markExerciseSolved(String lessonId) {
        if (lessonId == null || lessonId.isEmpty()) {
            return;
        }
        database.update(lessonId, true, true);
    }

    @Override
    public void reset() {
        database.getWritableDatabase().delete(ProgressDatabase.TABLE_PROGRESS, null, null);
    }

    /** Schéma de la base de progression. */
    private static final class ProgressDatabase extends SQLiteOpenHelper {

        static final String TABLE_PROGRESS = "progression";
        static final String COLUMN_LESSON_ID = "lecon_id";
        static final String COLUMN_LESSON_READ = "lecon_lue";
        static final String COLUMN_EXERCISE_SOLVED = "exercice_reussi";

        private static final String DATABASE_NAME = "dystos.db";
        private static final int DATABASE_VERSION = 1;

        ProgressDatabase(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS " + TABLE_PROGRESS + " ("
                            + COLUMN_LESSON_ID + " TEXT PRIMARY KEY NOT NULL, "
                            + COLUMN_LESSON_READ + " INTEGER NOT NULL DEFAULT 0, "
                            + COLUMN_EXERCISE_SOLVED + " INTEGER NOT NULL DEFAULT 0"
                            + ")");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Le contenu est versionne avec l'application : reconstruire vaut
            // plus sur qu'une migration dont personne ne teste le chemin.
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_PROGRESS);
            onCreate(db);
        }

        void update(String lessonId, boolean lessonRead, boolean exerciseSolved) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_LESSON_ID, lessonId);
            values.put(COLUMN_LESSON_READ, lessonRead ? 1 : 0);
            values.put(COLUMN_EXERCISE_SOLVED, exerciseSolved ? 1 : 0);
            getWritableDatabase()
                    .insertWithOnConflict(
                            TABLE_PROGRESS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }
}