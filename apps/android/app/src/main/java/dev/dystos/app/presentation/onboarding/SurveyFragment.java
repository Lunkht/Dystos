package dev.dystos.app.presentation.onboarding;

import android.os.Bundle;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.dystos.app.R;
import dev.dystos.app.core.SurveyPreferences;
import dev.dystos.app.presentation.MainActivity;

public final class SurveyFragment extends Fragment {

    private int currentStep = 0;
    private View rootView;

    private static final List<String> SUBTITLES = Arrays.asList(
            "Commençons par personnaliser votre expérience !",
            "Super ! Créons le parcours d'apprentissage idéal pour vous.",
            "Super ! Dystos propose du contenu pour tous les niveaux.",
            "Cool ! Dystos est encore meilleur en apprenant avec d'autres.",
            "Il y a des sujets de programmation Dys à choisir.",
            "Quelques minutes par jour suffisent pour progresser.",
            "Obtenez des réponses à vos questions à la demande. Illimité en mode Apprentissage."
    );

    private static final List<String> QUESTIONS = Arrays.asList(
            "Lequel vous décrit le mieux ?",
            "Depuis combien de temps êtes-vous développeur professionnel ?",
            "Quel est votre principal objectif d'apprentissage ?",
            "Connaissez-vous quelqu'un d'autre qui souhaite apprendre à coder ?",
            "Quelles compétences Dys souhaitez-vous apprendre ?",
            "Définir un objectif quotidien",
            "Nouveau ! Le mentor IA vous offre un coaching personnalisé"
    );

    private static final List<List<String>> OPTIONS = Arrays.asList(
            Arrays.asList(
                    "Développeur professionnel",
                    "Professionnel non-développeur",
                    "Étudiant",
                    "Manager / Cadre",
                    "Autre"
            ),
            Arrays.asList(
                    "Moins d'1 an",
                    "1 - 3 ans",
                    "3 - 5 ans",
                    "Plus de 5 ans"
            ),
            Arrays.asList(
                    "Améliorer mes compétences back-end",
                    "Améliorer mes compétences front-end",
                    "Améliorer mes compétences full-stack",
                    "Apprendre les compétences data",
                    "Créer une habitude d'apprentissage",
                    "Trouver un emploi",
                    "Autre"
            ),
            Arrays.asList(
                    "Oui !",
                    "Je ne sais pas",
                    "Non"
            ),
            Arrays.asList(), // Step 4: Skills grid
            Arrays.asList(
                    "5 min / jour  (Décontracté)",
                    "10 min / jour  (Régulier)",
                    "15 min / jour  (Sérieux)",
                    "20 min / jour  (Intense)"
            ),
            Arrays.asList() // Step 6: AI mentor intro
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        loadStep(currentStep, inflater, container);
        return rootView;
    }

    private void loadStep(int step, LayoutInflater inflater, ViewGroup container) {
        if (step < 0 || step >= QUESTIONS.size()) {
            finishSurvey();
            return;
        }

        if (step == 4) {
            // Skills grid step (Dys topics)
            rootView = inflater.inflate(R.layout.fragment_survey_skills, container, false);
            ImageButton btnBack = rootView.findViewById(R.id.btn_back_skills);
            RecyclerView recyclerView = rootView.findViewById(R.id.recycler_skills);
            MaterialButton btnContinue = rootView.findViewById(R.id.btn_continue_skills);

            recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));

            List<SkillItem> skills = Arrays.asList(
                    new SkillItem("Introduction à Dys", "12.4K apprenants"),
                    new SkillItem("Variables & Types", "9.8K apprenants"),
                    new SkillItem("Conditions & Boucles", "15.2K apprenants"),
                    new SkillItem("Fonctions & Portée", "8.6K apprenants"),
                    new SkillItem("Listes & Tableaux", "11.1K apprenants"),
                    new SkillItem("Gestion d'erreurs", "6.5K apprenants"),
                    new SkillItem("Algorithmique Dys", "14.3K apprenants"),
                    new SkillItem("Projets Pratiques", "18.9K apprenants"),
                    new SkillItem("Expressions & Syntaxe", "7.2K apprenants"),
                    new SkillItem("Entrées & Sorties", "8.4K apprenants"),
                    new SkillItem("Structures de Données", "10.5K apprenants"),
                    new SkillItem("Débogage de code", "9.1K apprenants"),
                    new SkillItem("Bonnes Pratiques", "13.0K apprenants"),
                    new SkillItem("Maîtrise de Dys", "20.4K apprenants")
            );

            Runnable updateButtonState = () -> {
                boolean anySelected = false;
                for (SkillItem item : skills) {
                    if (item.isSelected()) {
                        anySelected = true;
                        break;
                    }
                }
                if (anySelected) {
                    btnContinue.setText("Continuer");
                    btnContinue.setBackgroundTintList(ColorStateList.valueOf(
                            requireContext().getColor(R.color.dystos_primary)));
                    btnContinue.setTextColor(
                            requireContext().getColor(R.color.dystos_on_primary));
                } else {
                    btnContinue.setText("Sélectionnez au moins une compétence");
                    btnContinue.setBackgroundTintList(ColorStateList.valueOf(
                            requireContext().getColor(R.color.dystos_surface_variant)));
                    btnContinue.setTextColor(
                            requireContext().getColor(R.color.dystos_on_surface));
                }
            };

            SkillsAdapter adapter = new SkillsAdapter(skills, updateButtonState);
            recyclerView.setAdapter(adapter);

            btnContinue.setOnClickListener(v -> {
                boolean anySelected = false;
                Set<String> selectedSkills = new HashSet<>();
                for (SkillItem item : skills) {
                    if (item.isSelected()) {
                        anySelected = true;
                        selectedSkills.add(item.getName());
                    }
                }
                if (anySelected) {
                    SurveyPreferences.saveSkills(requireContext(), selectedSkills);
                    currentStep++;
                    loadStep(currentStep, inflater, container);
                    updateContainerView();
                }
            });

            btnBack.setOnClickListener(v -> {
                currentStep--;
                loadStep(currentStep, inflater, container);
                updateContainerView();
            });

        } else if (step == 6) {
            // AI Mentor intro step
            rootView = inflater.inflate(R.layout.fragment_survey_mentor, container, false);
            ImageButton btnBack = rootView.findViewById(R.id.btn_back_mentor);
            MaterialButton btnGotIt = rootView.findViewById(R.id.btn_got_it);

            btnGotIt.setOnClickListener(v -> finishSurvey());
            btnBack.setOnClickListener(v -> {
                currentStep--;
                loadStep(currentStep, inflater, container);
                updateContainerView();
            });

        } else {
            // Standard vertical options step (0, 1, 2, 3, 5)
            rootView = inflater.inflate(R.layout.fragment_survey, container, false);
            ImageButton btnBack = rootView.findViewById(R.id.btn_back);
            TextView tvSubtitle = rootView.findViewById(R.id.tv_subtitle);
            TextView tvQuestion = rootView.findViewById(R.id.tv_question);
            RecyclerView recyclerView = rootView.findViewById(R.id.recycler_options);

            recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
            tvSubtitle.setText(SUBTITLES.get(step));
            tvQuestion.setText(QUESTIONS.get(step));

            List<String> currentOptions = OPTIONS.get(step);
            SurveyAdapter adapter = new SurveyAdapter(currentOptions, (position, option) -> {
                SurveyPreferences.saveAnswer(requireContext(), step, option);
                currentStep++;
                loadStep(currentStep, inflater, container);
                updateContainerView();
            });
            recyclerView.setAdapter(adapter);

            btnBack.setOnClickListener(v -> {
                if (currentStep > 0) {
                    currentStep--;
                    loadStep(currentStep, inflater, container);
                    updateContainerView();
                } else {
                    requireActivity().onBackPressed();
                }
            });
        }
    }

    private void updateContainerView() {
        ViewGroup container = (ViewGroup) getView();
        if (container != null) {
            container.removeAllViews();
            container.addView(rootView);
        }
    }

    private void finishSurvey() {
        SurveyPreferences.setCompleted(requireContext(), true);
        ((MainActivity) requireActivity()).openAccountPrompt();
    }
}
