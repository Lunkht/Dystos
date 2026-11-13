package dev.dystos.app.presentation.ide;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.GravityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import dev.dystos.app.R;
import dev.dystos.app.core.IdePreferences;
import dev.dystos.app.databinding.FragmentIdeBinding;
import dev.dystos.app.domain.model.CheckResult;
import dev.dystos.app.ide.Language;
import dev.dystos.app.ide.LanguageRegistry;
import dev.dystos.app.language.CodeAnalysis;
import dev.dystos.app.presentation.BaseFragment;
import dev.dystos.app.presentation.exercise.ShareCode;

/** IDE : éditeur multi-langage avec coloration, erreurs en direct et console. */
public final class IdeFragment extends BaseFragment {

    private static final String KEY_CODE = "code";

    private FragmentIdeBinding binding;
    private Language currentLanguage;
    private final List<EditorTab> openTabs = new ArrayList<>();
    private int activeTab = -1;

    private final List<Integer> searchMatches = new ArrayList<>();
    private int searchIndex = -1;
    private int searchLength = 0;

    /** Un fichier ouvert dans l'IDE : son titre et son contenu en mémoire. */
    private static final class EditorTab {
        final String title;
        String content;

        EditorTab(String title, String content) {
            this.title = title;
            this.content = content;
        }
    }

    private final ActivityResultLauncher<String[]> openDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null && isAdded()) {
                    try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
                         BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        String title = queryDisplayName(uri);
                        if (title == null || title.isEmpty()) {
                            title = "fichier." + currentLanguage.fileExtension();
                        }
                        openTab(title, sb.toString());
                        Toast.makeText(requireContext(), "Fichier ouvert avec succès !", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "Erreur lors de l'ouverture du fichier", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    private final ActivityResultLauncher<String> createDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/plain"), uri -> {
                if (uri != null && isAdded()) {
                    try (OutputStream outputStream = requireContext().getContentResolver().openOutputStream(uri);
                         BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
                        writer.write(binding.codeInput.getText().toString());
                        Toast.makeText(requireContext(), "Fichier enregistré avec succès !", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "Erreur lors de l'enregistrement", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected int layoutId() {
        return R.layout.fragment_ide;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentIdeBinding.bind(view);

        applyLanguage(readLanguage());
        openTabs.clear();
        boolean hasSavedCode = savedInstanceState != null
                && savedInstanceState.getString(KEY_CODE) != null;
        openTabs.add(new EditorTab(
                defaultFileName(),
                hasSavedCode
                        ? savedInstanceState.getString(KEY_CODE)
                        : currentLanguage.defaultCode()));
        activeTab = 0;
        buildTabs();

        binding.codeInput.setLineNumbers(binding.lineNumbers);
        binding.codeInput.setLineNumberColor(getResources().getColor(R.color.dystos_outline, requireContext().getTheme()));
        binding.codeInput.applyIdePreferences();
        binding.codeInput.setText(openTabs.get(activeTab).content);
        binding.codeInput.setSelection(binding.codeInput.getText().length());
        binding.codeInput.resetHistory();
        updateUndoRedo();
        showLiveError();

        binding.codeInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (activeTab >= 0 && activeTab < openTabs.size()) {
                    openTabs.get(activeTab).content = s.toString();
                }
                showLiveError();
                if (binding.searchBar.getVisibility() == View.VISIBLE) {
                    updateSearchMatches();
                }
            }
        });

        binding.btnFolder.setOnClickListener(v -> binding.drawerLayout.openDrawer(GravityCompat.START));
        setupNavigationDrawer();

        if (binding.search != null) {
            binding.search.setOnClickListener(v -> {
                binding.drawerLayout.closeDrawer(GravityCompat.START);
                toggleSearch();
            });
        }
        if (binding.commitGit != null) {
            binding.commitGit.setOnClickListener(v -> {
                binding.drawerLayout.closeDrawer(GravityCompat.START);
                new AlertDialog.Builder(requireContext())
                        .setTitle("Git — Status")
                        .setMessage("Branche active : main\n\nTous les fichiers de l'espace de travail sont synchronisés avec Git local.")
                        .setPositiveButton("OK", null)
                        .show();
            });
        }
        if (binding.github != null) {
            binding.github.setOnClickListener(v -> {
                binding.drawerLayout.closeDrawer(GravityCompat.START);
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com"));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Impossible d'ouvrir le navigateur", Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (binding.share != null) {
            binding.share.setOnClickListener(v -> {
                binding.drawerLayout.closeDrawer(GravityCompat.START);
                String code = binding.codeInput.getText().toString();
                ShareCode.text(requireContext(), code, "Code Dystos IDE");
            });
        }

        binding.btnOpen.setOnClickListener(v -> openDocumentLauncher.launch(new String[]{"*/*"}));
        if (binding.btnAi != null) {
            binding.btnAi.setOnClickListener(v -> showAiAssistantDialog());
        }

        binding.btnReset.setOnClickListener(v -> resetCode());
        binding.btnRun.setOnClickListener(v -> runCode());
        binding.btnClear.setOnClickListener(v -> binding.resultCard.setVisibility(View.GONE));
        binding.btnIdeSettings.setOnClickListener(v ->
                ((dev.dystos.app.presentation.MainActivity) requireActivity()).openIdeSettings());

        binding.btnSave.setOnClickListener(v ->
                createDocumentLauncher.launch(activeTabTitle()));

        binding.btnUndo.setOnClickListener(v -> {
            binding.codeInput.undo();
            updateUndoRedo();
        });
        binding.btnRedo.setOnClickListener(v -> {
            binding.codeInput.redo();
            updateUndoRedo();
        });
        binding.btnSearch.setOnClickListener(v -> toggleSearch());

        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateSearchMatches();
            }
        });
        binding.btnPrev.setOnClickListener(v -> goToPrevMatch());
        binding.btnNext.setOnClickListener(v -> goToNextMatch());
        binding.btnReplace.setOnClickListener(v -> replaceCurrentMatch());
        binding.btnReplaceAll.setOnClickListener(v -> replaceAllMatches());
        binding.btnCloseSearch.setOnClickListener(v -> binding.searchBar.setVisibility(View.GONE));
    }

    private void showAiAssistantDialog() {
        View sheetView = LayoutInflater.from(requireContext())
                .inflate(R.layout.code_gen_assist_dialog, null);
        EditText etPrompt = sheetView.findViewById(R.id.editTextText);
        EditText etOutput = sheetView.findViewById(R.id.editTextTextMultiLine);
        android.widget.ImageView ivBot = sheetView.findViewById(R.id.imageView);
        if (ivBot != null) {
            ivBot.setImageResource(R.drawable.bjimo);
        }

        if (etPrompt != null) {
            etPrompt.setText("");
            etPrompt.setHint("Que souhaitez-vous coder ?");
            etPrompt.setTextColor(requireContext().getColor(R.color.dystos_on_surface));
        }

        if (etOutput != null) {
            etOutput.setHint("Le code généré apparaîtra ici...");
            etOutput.setTextColor(requireContext().getColor(R.color.dystos_code_on_background));
            etOutput.setTextSize(14f);
            etOutput.setMinHeight(180);
        }

        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        sheet.setContentView(sheetView);

        Runnable generate = () -> {
            if (etPrompt != null && etOutput != null) {
                generateAiCode(etPrompt.getText().toString(), etOutput);
            }
        };

        View btnGenerer = sheetView.findViewById(R.id.btn_generer);
        if (btnGenerer != null) {
            btnGenerer.setOnClickListener(v -> generate.run());
        }

        View btnInserer = sheetView.findViewById(R.id.btn_inserer);
        if (btnInserer != null) {
            btnInserer.setOnClickListener(v -> {
                if (etOutput != null) {
                    String generated = etOutput.getText().toString();
                    if (!generated.isEmpty()) {
                        insertIntoEditor(generated);
                        Toast.makeText(requireContext(), "Code inséré dans l'IDE !", Toast.LENGTH_SHORT).show();
                    }
                }
                sheet.dismiss();
            });
        }

        View btnFermer = sheetView.findViewById(R.id.btn_fermer);
        if (btnFermer != null) {
            btnFermer.setOnClickListener(v -> sheet.dismiss());
        }

        sheet.show();
    }

    /** Insère le texte généré au niveau du curseur de l'onglet actif. */
    private void insertIntoEditor(String code) {
        Editable text = binding.codeInput.getText();
        int start = Math.max(binding.codeInput.getSelectionStart(), 0);
        int end = Math.max(binding.codeInput.getSelectionEnd(), 0);
        text.replace(Math.min(start, end), Math.max(start, end), code);
    }

    private void generateAiCode(String prompt, EditText outputView) {
        String p = prompt.toLowerCase();
        String code;
        if (p.contains("factorielle") || p.contains("fact")) {
            code = "def factorielle(n: int) -> int:\n    if n <= 1:\n        return 1\n    return n * factorielle(n - 1)\n\nprint(factorielle(5))\n";
        } else if (p.contains("boucle") || p.contains("for") || p.contains("while")) {
            code = "for i in range(1, 6):\n    print(f\"Ligne {i}\")\n";
        } else if (p.contains("fonction") || p.contains("def")) {
            code = "def saluer(nom: string):\n    print(f\"Bonjour, {nom} !\")\n\nsaluer(\"Dystos\")\n";
        } else if (p.contains("liste") || p.contains("tableau")) {
            code = "val nombres: list = [1, 2, 3, 4, 5]\nprint(nombres)\n";
        } else {
            code = "val message: string = \"Bonjour depuis Bjimo AI !\"\nprint(message)\n";
        }
        outputView.setText(code);
    }

    /**
     * Langage mémorisé dans les paramètres de l'IDE, ou langage par défaut si
     * la préférence est vide ou inconnue.
     */
    private Language readLanguage() {
        String id = IdePreferences.languageId(requireContext());
        return LanguageRegistry.exists(id)
                ? LanguageRegistry.get(id) : LanguageRegistry.defaultLanguage();
    }

    /**
     * Re-synchronise l'éditeur avec les paramètres au retour de l'écran de
     * réglages : le code n'est remplacé que s'il est vide ou jamais modifié.
     */
    @Override
    public void onResume() {
        super.onResume();
        if (binding == null) {
            return;
        }
        binding.codeInput.applyIdePreferences();
        Language stored = readLanguage();
        if (currentLanguage != null && currentLanguage.id().equals(stored.id())) {
            return;
        }
        if (openTabs.isEmpty()) {
            return;
        }
        String text = binding.codeInput.getText().toString();
        boolean untouched = text.isEmpty()
                || (currentLanguage != null && text.equals(currentLanguage.defaultCode()));
        applyLanguage(stored);
        if (untouched) {
            EditorTab tab = openTabs.get(activeTab);
            tab.content = stored.defaultCode();
            buildTabs();
            binding.codeInput.setText(tab.content);
            binding.codeInput.setSelection(binding.codeInput.getText().length());
            binding.codeInput.resetHistory();
            updateUndoRedo();
            binding.resultCard.setVisibility(View.GONE);
        }
    }

    private void setupNavigationDrawer() {
        File filesDir = requireContext().getFilesDir();
        File[] files = filesDir.listFiles((dir, name) -> !name.startsWith(".") && !name.endsWith(".xml") && !name.contains("prefs"));
        if (files == null || files.length == 0) {
            try {
                File defaultFile = new File(filesDir, "programme." + (currentLanguage != null ? currentLanguage.fileExtension() : "dys"));
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(defaultFile)))) {
                    writer.write(binding.codeInput.getText().toString());
                }
                files = filesDir.listFiles((dir, name) -> !name.startsWith(".") && !name.endsWith(".xml") && !name.contains("prefs"));
            } catch (Exception ignored) {}
        }

        final File[] currentFiles = files != null ? files : new File[0];

        RecyclerView recyclerView = binding.drawerRecyclerFiles;
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        class FileAdapter extends RecyclerView.Adapter<FileAdapter.Holder> {
            @NonNull
            @Override
            public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_workspace_file, parent, false);
                return new Holder(v);
            }

            @Override
            public void onBindViewHolder(@NonNull Holder holder, int position) {
                File file = currentFiles[position];
                holder.name.setText(file.getName());
                holder.itemView.setOnClickListener(v -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        openTab(file.getName(), sb.toString());
                        Toast.makeText(requireContext(), "Ouvert : " + file.getName(), Toast.LENGTH_SHORT).show();
                        binding.drawerLayout.closeDrawer(GravityCompat.START);
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "Erreur de lecture", Toast.LENGTH_SHORT).show();
                    }
                });
                holder.delete.setOnClickListener(v -> {
                    androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(requireContext(), holder.delete);
                    popup.getMenu().add(0, 1, 0, "Ouvrir");
                    popup.getMenu().add(0, 2, 1, "Copier");
                    popup.getMenu().add(0, 3, 2, "Partager");
                    popup.getMenu().add(0, 4, 3, "Supprimer");

                    popup.setOnMenuItemClickListener(item -> {
                        int itemId = item.getItemId();
                        if (itemId == 1) {
                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
                                StringBuilder sb = new StringBuilder();
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    sb.append(line).append("\n");
                                }
                                openTab(file.getName(), sb.toString());
                                Toast.makeText(requireContext(), "Ouvert : " + file.getName(), Toast.LENGTH_SHORT).show();
                                binding.drawerLayout.closeDrawer(GravityCompat.START);
                            } catch (Exception e) {
                                Toast.makeText(requireContext(), "Erreur de lecture", Toast.LENGTH_SHORT).show();
                            }
                        } else if (itemId == 2) {
                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
                                StringBuilder sb = new StringBuilder();
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    sb.append(line).append("\n");
                                }
                                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                                android.content.ClipData clip = android.content.ClipData.newPlainText(file.getName(), sb.toString());
                                if (clipboard != null) {
                                    clipboard.setPrimaryClip(clip);
                                    Toast.makeText(requireContext(), "Contenu de " + file.getName() + " copié !", Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception e) {
                                Toast.makeText(requireContext(), "Erreur de copie", Toast.LENGTH_SHORT).show();
                            }
                        } else if (itemId == 3) {
                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
                                StringBuilder sb = new StringBuilder();
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    sb.append(line).append("\n");
                                }
                                ShareCode.text(requireContext(), sb.toString(), file.getName());
                            } catch (Exception e) {
                                Toast.makeText(requireContext(), "Erreur de partage", Toast.LENGTH_SHORT).show();
                            }
                        } else if (itemId == 4) {
                            if (file.delete()) {
                                Toast.makeText(requireContext(), "Supprimé : " + file.getName(), Toast.LENGTH_SHORT).show();
                                setupNavigationDrawer();
                            }
                        }
                        return true;
                    });
                    popup.show();
                });
            }

            @Override
            public int getItemCount() {
                return currentFiles.length;
            }

            class Holder extends RecyclerView.ViewHolder {
                final TextView name;
                final View delete;
                Holder(@NonNull View itemView) {
                    super(itemView);
                    name = itemView.findViewById(R.id.file_name);
                    delete = itemView.findViewById(R.id.btn_delete);
                }
            }
        }

        recyclerView.setAdapter(new FileAdapter());

        if (binding.btnDrawerNew != null) {
            binding.btnDrawerNew.setOnClickListener(v -> {
                EditText input = new EditText(requireContext());
                String ext = currentLanguage != null ? currentLanguage.fileExtension() : "dys";
                input.setHint("mon_programme." + ext);
                new AlertDialog.Builder(requireContext())
                        .setTitle("Nouveau fichier")
                        .setView(input)
                        .setPositiveButton("Créer", (d, w) -> {
                            String name = input.getText().toString().trim();
                            if (!name.isEmpty()) {
                                if (!name.contains(".")) {
                                    name = name + "." + ext;
                                }
                                File newFile = new File(filesDir, name);
                                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(newFile)))) {
                                    writer.write(currentLanguage != null ? currentLanguage.defaultCode() : "");
                                    openTab(name, currentLanguage != null ? currentLanguage.defaultCode() : "");
                                    Toast.makeText(requireContext(), "Fichier " + name + " créé !", Toast.LENGTH_SHORT).show();
                                    setupNavigationDrawer();
                                } catch (Exception e) {
                                    Toast.makeText(requireContext(), "Erreur de création", Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setNegativeButton("Annuler", null)
                        .show();
            });
        }
    }

    /** Applique un langage à l'éditeur : lexer, barre de symboles et code de départ. */
    private void applyLanguage(Language language) {
        currentLanguage = language;
        binding.codeInput.setLanguage(language);
        binding.symbolBar.bind(language, binding.codeInput);
    }

    /** Nom du fichier créé par défaut, selon l'extension du langage actif. */
    private String defaultFileName() {
        String ext = currentLanguage != null ? currentLanguage.fileExtension() : "dys";
        return "programme." + ext;
    }

    /** Titre de l'onglet actif (utilisé comme nom proposé à l'enregistrement). */
    private String activeTabTitle() {
        return openTabs.isEmpty() ? defaultFileName() : openTabs.get(activeTab).title;
    }

    private String queryDisplayName(Uri uri) {
        try (android.database.Cursor cursor = requireContext().getContentResolver()
                .query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Reconstruit la barre d'onglets, en surlignant celui qui est actif. */
    private void buildTabs() {
        binding.tabContainer.removeAllViews();
        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < openTabs.size(); i++) {
            EditorTab tab = openTabs.get(i);
            View item = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_editor_tab, binding.tabContainer, false);
            TextView title = item.findViewById(R.id.tab_title);
            title.setText(tab.title);
            item.setBackgroundResource(i == activeTab
                    ? R.drawable.ide_tab_bg_active : R.drawable.ide_tab_bg);

            final int index = i;
            item.setOnClickListener(v -> selectTab(index));
            item.findViewById(R.id.btn_close_tab).setOnClickListener(v -> closeTab(index));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd((int) (8 * density));
            binding.tabContainer.addView(item, params);
        }
    }

    /** Mémoire le contenu de l'onglet courant puis en charge un autre. */
    private void selectTab(int index) {
        if (openTabs.isEmpty() || index < 0 || index >= openTabs.size()) {
            return;
        }
        if (activeTab >= 0 && activeTab < openTabs.size()) {
            openTabs.get(activeTab).content = binding.codeInput.getText().toString();
        }
        activeTab = index;
        buildTabs();
        binding.codeInput.setLineNumbers(binding.lineNumbers);
        binding.codeInput.setLineNumberColor(getResources().getColor(R.color.dystos_outline, requireContext().getTheme()));
        binding.codeInput.setText(openTabs.get(activeTab).content);
        binding.codeInput.setSelection(binding.codeInput.getText().length());
        binding.codeInput.resetHistory();
        updateUndoRedo();
    }

    /** Ouvre un fichier : l'active s'il existe déjà, sinon crée un onglet. */
    private void openTab(String title, String content) {
        for (int i = 0; i < openTabs.size(); i++) {
            if (openTabs.get(i).title.equals(title)) {
                selectTab(i);
                return;
            }
        }
        openTabs.add(new EditorTab(title, content));
        selectTab(openTabs.size() - 1);
    }

    /** Ferme un onglet ; si c'était le dernier, en recrée un vierge. */
    private void closeTab(int index) {
        if (openTabs.isEmpty() || index < 0 || index >= openTabs.size()) {
            return;
        }
        openTabs.remove(index);
        if (openTabs.isEmpty()) {
            openTabs.add(new EditorTab(defaultFileName(), currentLanguage.defaultCode()));
            activeTab = 0;
        } else if (index < openTabs.size()) {
            activeTab = index;
        } else {
            activeTab = openTabs.size() - 1;
        }
        selectTab(activeTab);
    }

    private void resetCode() {
        if (currentLanguage == null || openTabs.isEmpty()) {
            return;
        }
        EditorTab tab = openTabs.get(activeTab);
        tab.content = currentLanguage.defaultCode();
        binding.codeInput.setText(tab.content);
        binding.codeInput.setSelection(binding.codeInput.getText().length());
        binding.codeInput.resetHistory();
        updateUndoRedo();
        binding.resultCard.setVisibility(View.GONE);
    }

    private void updateUndoRedo() {
        binding.btnUndo.setAlpha(binding.codeInput.hasUndo() ? 1f : 0.35f);
        binding.btnRedo.setAlpha(binding.codeInput.hasRedo() ? 1f : 0.35f);
    }

    private void toggleSearch() {
        boolean visible = binding.searchBar.getVisibility() == View.GONE;
        binding.searchBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            binding.searchInput.requestFocus();
            binding.searchInput.setSelection(binding.searchInput.length());
            updateSearchMatches();
        }
    }

    /** Recalcule toutes les occurrences de la recherche (insensible à la casse). */
    private void updateSearchMatches() {
        searchMatches.clear();
        searchIndex = -1;
        String query = binding.searchInput.getText().toString();
        searchLength = query.length();
        String text = binding.codeInput.getText().toString();
        if (query.isEmpty() || text.isEmpty()) {
            binding.searchCount.setText("0/0");
            return;
        }
        String lowerText = text.toLowerCase();
        String lowerQuery = query.toLowerCase();
        int from = 0;
        while ((from = lowerText.indexOf(lowerQuery, from)) >= 0) {
            searchMatches.add(from);
            from += searchLength;
        }
        binding.searchCount.setText("0/" + searchMatches.size());
    }

    private void goToNextMatch() {
        if (searchMatches.isEmpty()) {
            return;
        }
        searchIndex = (searchIndex + 1) % searchMatches.size();
        showMatch();
    }

    private void goToPrevMatch() {
        if (searchMatches.isEmpty()) {
            return;
        }
        searchIndex = (searchIndex - 1 + searchMatches.size()) % searchMatches.size();
        showMatch();
    }

    private void showMatch() {
        int start = searchMatches.get(searchIndex);
        binding.codeInput.setSelection(start, start + searchLength);
        binding.codeInput.requestFocus();
        binding.searchCount.setText((searchIndex + 1) + "/" + searchMatches.size());
    }

    private void replaceCurrentMatch() {
        if (searchMatches.isEmpty()) {
            return;
        }
        if (searchIndex < 0) {
            searchIndex = 0;
        }
        int start = searchMatches.get(searchIndex);
        binding.codeInput.getText()
                .replace(start, start + searchLength, binding.replaceInput.getText().toString());
        updateSearchMatches();
    }

    private void replaceAllMatches() {
        String query = binding.searchInput.getText().toString();
        if (query.isEmpty()) {
            return;
        }
        String replace = binding.replaceInput.getText().toString();
        Editable text = binding.codeInput.getText();
        String lower = text.toString().toLowerCase();
        String lowerQuery = query.toLowerCase();
        List<Integer> starts = new ArrayList<>();
        int from = 0;
        while ((from = lower.indexOf(lowerQuery, from)) >= 0) {
            starts.add(from);
            from += query.length();
        }
        for (int i = starts.size() - 1; i >= 0; i--) {
            int start = starts.get(i);
            text.replace(start, start + query.length(), replace);
        }
        updateSearchMatches();
    }

    private void showLiveError() {
        CodeAnalysis.Problem problem = binding.codeInput.getProblem();
        if (problem == null || binding.codeInput.getText().length() == 0) {
            binding.liveError.setVisibility(View.GONE);
            return;
        }
        binding.liveError.setVisibility(View.VISIBLE);
        binding.liveError.setText(getString(R.string.error_at_line,
                problem.line(), problem.message()));
    }

    private void runCode() {
        if (currentLanguage == null) {
            return;
        }
        String code = binding.codeInput.getText().toString();

        binding.resultCard.setVisibility(View.VISIBLE);
        binding.outputText.setText("Exécution…");
        binding.resultTitle.setText("Console");
        binding.resultTitle.setTextColor(getResources().getColor(
                R.color.dystos_outline, requireContext().getTheme()));

        currentLanguage.runAsync(code, result -> {
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> showResult(result));
            }
        });
    }

    private void showResult(CheckResult result) {
        if (binding == null) {
            return;
        }
        binding.resultTitle.setText("Console — Résultat");
        binding.outputText.setText(result.actualOutput().isEmpty()
                ? "(Programme exécuté sans sortie)" : result.actualOutput());

        if (result.kind() == CheckResult.Kind.SUCCESS) {
            binding.resultTitle.setText("Console — Succès");
            binding.resultTitle.setTextColor(getResources().getColor(
                    R.color.dystos_primary, requireContext().getTheme()));
        } else {
            binding.resultTitle.setText(result.message().isEmpty()
                    ? "Erreur d'exécution"
                    : getString(R.string.error_at_line, result.line(), result.message()));
            binding.resultTitle.setTextColor(getResources().getColor(
                    R.color.dystos_error, requireContext().getTheme()));
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (binding != null && binding.codeInput.getText() != null) {
            outState.putString(KEY_CODE, binding.codeInput.getText().toString());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
