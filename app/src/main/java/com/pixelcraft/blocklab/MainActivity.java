package com.pixelcraft.blocklab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Native, offline-first pixel editor for Minecraft Bedrock texture files. */
public final class MainActivity extends Activity {
    private static final int REQUEST_IMPORT = 11;
    private static final int REQUEST_EXPORT_PNG = 12;
    private static final int REQUEST_EXPORT_PACK = 13;

    private static final int BG = Color.rgb(11, 16, 14);
    private static final int SURFACE = Color.rgb(18, 27, 22);
    private static final int SURFACE_2 = Color.rgb(25, 37, 30);
    private static final int ACCENT = Color.rgb(157, 229, 108);
    private static final int WHITE = Color.rgb(243, 245, 239);
    private static final int MUTED = Color.rgb(163, 174, 165);
    private static final int[] PALETTE = new int[] {
            0xFF9DE56C, 0xFF5CBB59, 0xFF347A43, 0xFFCEE49A,
            0xFFE7C655, 0xFFB578D5, 0xFF6265A8, 0xFFEAF1E8,
            0xFF26372C, 0xFF4D5453
    };
    private static final String[] TARGET_LABELS = new String[] {
            "Верх травы", "Блок земли", "Камень", "Песок",
            "Алмазная руда", "Доски дуба", "Листва дуба"
    };
    private static final String[] TARGET_KEYS = new String[] {
            "grass_top", "dirt", "stone", "sand", "diamond_ore", "planks_oak", "leaves_oak"
    };

    private FrameLayout windowRoot;
    private SharedPreferences preferences;
    private TextureDocument currentTexture;
    private TextureCanvasView editorCanvas;
    private TextView gridButton;
    private TextView undoButton;
    private TextView redoButton;
    private TextView colorValue;
    private TextView targetButton;
    private final ArrayList<TextView> toolButtons = new ArrayList<>();
    private final ArrayList<TextView> paletteButtons = new ArrayList<>();
    private int selectedColor = ACCENT;
    private int selectedTool = TextureCanvasView.TOOL_BRUSH;
    private boolean gridEnabled = true;
    private int selectedTarget = 0;
    private String lastExportedPackName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("blocklab-project", MODE_PRIVATE);
        configureSystemBars();
        currentTexture = loadSavedTexture();
        if (currentTexture == null) currentTexture = TextureDocument.grass("Трава", 16);
        renderHome();
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        View decor = window.getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        windowRoot = new FrameLayout(this);
        windowRoot.setBackgroundColor(BG);
        windowRoot.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(windowRoot);
    }

    private void showScreen(View screen) {
        windowRoot.removeAllViews();
        windowRoot.addView(screen, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    // ---------------------------------------------------------------------
    // Home
    // ---------------------------------------------------------------------

    private void renderHome() {
        editorCanvas = null;
        gridButton = null;
        undoButton = null;
        redoButton = null;
        colorValue = null;
        targetButton = null;
        toolButtons.clear();
        paletteButtons.clear();
        LinearLayout screen = column();
        screen.setBackgroundColor(BG);
        screen.addView(buildBrandHeader(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(70)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = column();
        body.setPadding(dp(22), dp(12), dp(22), dp(26));

        TextView eyebrow = text("●  МАСТЕРСКАЯ ТЕКСТУР BEDROCK", 10, ACCENT, true);
        eyebrow.setLetterSpacing(0.11f);
        body.addView(eyebrow);
        addGap(body, 11);

        TextView title = text("Твори свой\nMinecraft.", 34, WHITE, true);
        title.setLineSpacing(dp(1), 1.02f);
        body.addView(title);
        addGap(body, 9);

        TextView intro = text("Рисуй пиксель за пикселем —\nи забирай текстуру прямо в игру.", 14, MUTED, false);
        intro.setLineSpacing(dp(4), 1.0f);
        body.addView(intro);
        addGap(body, 22);

        body.addView(buildContinueCard());
        addGap(body, 12);

        LinearLayout actionRow = row();
        TextView newButton = button("＋  НОВЫЙ ПРОЕКТ", ACCENT, BG, 11, true);
        newButton.setOnClickListener(v -> showNewTextureDialog());
        actionRow.addView(newButton, weightedParams(1, 56));
        View actionGap = new View(this);
        actionRow.addView(actionGap, new LinearLayout.LayoutParams(dp(10), 1));
        TextView importButton = button("↥  ИМПОРТ PNG", SURFACE_2, WHITE, 12, true);
        importButton.setOnClickListener(v -> launchImportPicker());
        actionRow.addView(importButton, weightedParams(1, 56));
        body.addView(actionRow);
        addGap(body, 28);

        LinearLayout section = row();
        TextView templatesTitle = text("ШАБЛОНЫ", 11, MUTED, true);
        templatesTitle.setLetterSpacing(0.15f);
        section.addView(templatesTitle, new LinearLayout.LayoutParams(0, dp(22), 1));
        TextView allLabel = text("НАЧНИ С ПРИМЕРА", 9, MUTED, true);
        allLabel.setLetterSpacing(0.08f);
        section.addView(allLabel);
        body.addView(section);
        addGap(body, 10);
        body.addView(buildTemplateShelf());
        addGap(body, 20);

        LinearLayout note = row();
        note.setGravity(Gravity.CENTER_VERTICAL);
        note.setPadding(dp(14), dp(12), dp(14), dp(12));
        note.setBackground(roundRect(0xFF101813, 0xFF202C23, 13, dp(1)));
        TextView spark = text("✦", 19, ACCENT, true);
        note.addView(spark, new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout noteCopy = column();
        TextView noteTitle = text("Локально и без лишних шагов", 12, WHITE, true);
        TextView noteBody = text("Проекты сохраняются на этом устройстве.", 10, MUTED, false);
        noteCopy.addView(noteTitle);
        addGap(noteCopy, 3);
        noteCopy.addView(noteBody);
        note.addView(noteCopy, new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(note);

        scroll.addView(body);
        screen.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        showScreen(screen);
    }

    private View buildBrandHeader() {
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(22), dp(7), dp(22), dp(4));
        LogoMark mark = new LogoMark(this);
        header.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout labels = column();
        labels.setPadding(dp(11), 0, 0, 0);
        TextView brand = text("БЛОКЛАБ", 13, WHITE, true);
        brand.setLetterSpacing(0.1f);
        TextView sub = text("PIXEL TEXTURE STUDIO", 8, MUTED, true);
        sub.setLetterSpacing(0.1f);
        labels.addView(brand);
        addGap(labels, 2);
        labels.addView(sub);
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        TextView version = text("PE  •  1.0", 9, ACCENT, true);
        version.setGravity(Gravity.CENTER);
        version.setPadding(dp(10), dp(7), dp(10), dp(7));
        version.setBackground(roundRect(0xFF17241B, 0xFF2C4030, 20, dp(1)));
        header.addView(version);
        return header;
    }

    private View buildContinueCard() {
        LinearLayout card = row();
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(17), dp(16), dp(13), dp(16));
        card.setBackground(roundRect(SURFACE, 0xFF27372B, 20, dp(1)));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> openEditor());

        LinearLayout copy = column();
        TextView badge = text("ПОСЛЕДНИЙ ПРОЕКТ", 9, ACCENT, true);
        badge.setLetterSpacing(0.12f);
        copy.addView(badge);
        addGap(copy, 7);
        TextView name = text(currentTexture.name, 22, WHITE, true);
        name.setMaxLines(1);
        copy.addView(name);
        addGap(copy, 3);
        TextView size = text(currentTexture.size + " × " + currentTexture.size + " px   ·   PNG", 11, MUTED, false);
        copy.addView(size);
        addGap(copy, 14);
        TextView open = button("В РЕДАКТОР  →", ACCENT, BG, 10, true);
        open.setPadding(dp(13), dp(11), dp(13), dp(11));
        open.setOnClickListener(v -> openEditor());
        copy.addView(open, new LinearLayout.LayoutParams(-2, -2));
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        TexturePreviewView preview = new TexturePreviewView(this);
        preview.setDocument(currentTexture);
        card.addView(preview, new LinearLayout.LayoutParams(dp(100), dp(100)));
        return card;
    }

    private View buildTemplateShelf() {
        HorizontalScrollView shelf = new HorizontalScrollView(this);
        shelf.setHorizontalScrollBarEnabled(false);
        shelf.setClipToPadding(false);
        LinearLayout items = row();
        items.setClipToPadding(false);
        TextureDocument[] templates = new TextureDocument[] {
                TextureDocument.grass("Трава", 16),
                TextureDocument.amethyst("Аметист", 16),
                TextureDocument.stone("Камень", 16)
        };
        String[] captions = new String[] { "Сочный блок", "Кристальная пыль", "Глубинный сланец" };
        for (int i = 0; i < templates.length; i++) {
            final TextureDocument template = templates[i];
            LinearLayout card = column();
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding(dp(12), dp(13), dp(12), dp(12));
            card.setBackground(roundRect(SURFACE, 0xFF243129, 17, dp(1)));
            card.setClickable(true);
            card.setFocusable(true);
            TexturePreviewView preview = new TexturePreviewView(this);
            preview.setDocument(template);
            card.addView(preview, new LinearLayout.LayoutParams(dp(69), dp(69)));
            addGap(card, 8);
            TextView name = text(template.name, 13, WHITE, true);
            name.setGravity(Gravity.CENTER);
            card.addView(name);
            addGap(card, 3);
            TextView caption = text(captions[i], 9, MUTED, false);
            caption.setGravity(Gravity.CENTER);
            caption.setMaxLines(1);
            card.addView(caption);
            card.setOnClickListener(v -> {
                currentTexture = template.copy(template.name);
                selectedTarget = 0;
                selectedColor = ACCENT;
                saveCurrentTexture();
                openEditor();
            });
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dp(142), dp(151));
            cardParams.rightMargin = dp(10);
            items.addView(card, cardParams);
        }
        shelf.addView(items);
        return shelf;
    }

    // ---------------------------------------------------------------------
    // Editor
    // ---------------------------------------------------------------------

    private void openEditor() {
        if (currentTexture == null) currentTexture = TextureDocument.grass("Трава", 16);
        saveCurrentTexture();
        renderEditor();
    }

    private void renderEditor() {
        LinearLayout screen = column();
        screen.setBackgroundColor(BG);
        screen.addView(buildEditorHeader(), new LinearLayout.LayoutParams(-1, dp(72)));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout body = column();
        body.setPadding(dp(19), dp(5), dp(19), dp(24));

        LinearLayout canvasTitle = row();
        TextView canvasLabel = text("ПОЛОТНО", 10, MUTED, true);
        canvasLabel.setLetterSpacing(0.13f);
        canvasTitle.addView(canvasLabel, new LinearLayout.LayoutParams(0, dp(34), 1));
        gridButton = smallPill("СЕТКА  ВКЛ", true);
        gridButton.setOnClickListener(v -> {
            gridEnabled = !gridEnabled;
            editorCanvas.setGridEnabled(gridEnabled);
            updateEditorControls();
        });
        canvasTitle.addView(gridButton);
        body.addView(canvasTitle);

        FrameLayout canvasCard = new FrameLayout(this);
        canvasCard.setBackground(roundRect(SURFACE, 0xFF28382D, 20, dp(1)));
        editorCanvas = new TextureCanvasView(this);
        editorCanvas.setDocument(currentTexture);
        editorCanvas.setColor(selectedColor);
        editorCanvas.setGridEnabled(gridEnabled);
        editorCanvas.setTool(selectedTool);
        editorCanvas.setListener(new TextureCanvasView.Listener() {
            @Override public void onColorPicked(int color) {
                selectedColor = color;
                selectedTool = TextureCanvasView.TOOL_BRUSH;
                editorCanvas.setColor(selectedColor);
                editorCanvas.setTool(selectedTool);
                updateEditorControls();
                Toast.makeText(MainActivity.this, "Цвет взят с полотна", Toast.LENGTH_SHORT).show();
            }
            @Override public void onTextureChanged() {
                saveCurrentTexture();
                updateEditorControls();
            }
            @Override public void onHistoryChanged() { updateHistoryButtons(); }
        });
        FrameLayout.LayoutParams canvasParams = new FrameLayout.LayoutParams(-1, dp(292));
        canvasCard.addView(editorCanvas, canvasParams);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, dp(310));
        body.addView(canvasCard, cardParams);

        LinearLayout dimensionRow = row();
        TextView dimensions = text(currentTexture.size + " × " + currentTexture.size + " px", 11, WHITE, true);
        dimensionRow.addView(dimensions, new LinearLayout.LayoutParams(0, dp(34), 1));
        TextView hint = text("КАСАЙСЯ, ЧТОБЫ РИСОВАТЬ", 8, MUTED, true);
        hint.setLetterSpacing(0.06f);
        dimensionRow.addView(hint);
        body.addView(dimensionRow);
        addGap(body, 6);

        LinearLayout paletteHeader = row();
        TextView paletteTitle = text("ПАЛИТРА", 10, MUTED, true);
        paletteTitle.setLetterSpacing(0.13f);
        paletteHeader.addView(paletteTitle, new LinearLayout.LayoutParams(0, dp(29), 1));
        colorValue = text(colorHex(selectedColor), 10, WHITE, true);
        paletteHeader.addView(colorValue);
        body.addView(paletteHeader);
        body.addView(buildPalette());
        addGap(body, 15);

        LinearLayout toolsTitle = row();
        TextView toolsLabel = text("ИНСТРУМЕНТЫ", 10, MUTED, true);
        toolsLabel.setLetterSpacing(0.13f);
        toolsTitle.addView(toolsLabel);
        body.addView(toolsTitle);
        addGap(body, 8);
        body.addView(buildToolRow());
        addGap(body, 9);

        LinearLayout utilityRow = row();
        undoButton = utilityButton("↶  ОТМЕНИТЬ");
        redoButton = utilityButton("↷  ПОВТОРИТЬ");
        undoButton.setOnClickListener(v -> undo());
        redoButton.setOnClickListener(v -> redo());
        utilityRow.addView(undoButton, weightedParams(1, 44));
        View utilityGap = new View(this);
        utilityRow.addView(utilityGap, new LinearLayout.LayoutParams(dp(9), 1));
        utilityRow.addView(redoButton, weightedParams(1, 44));
        body.addView(utilityRow);
        addGap(body, 17);

        LinearLayout targetRow = row();
        LinearLayout targetCopy = column();
        TextView targetLabel = text("ТЕКСТУРА В ИГРЕ", 9, MUTED, true);
        targetLabel.setLetterSpacing(0.12f);
        TextView targetHint = text("Выбери, какой блок заменить в Bedrock", 11, WHITE, false);
        targetCopy.addView(targetLabel);
        addGap(targetCopy, 4);
        targetCopy.addView(targetHint);
        targetRow.addView(targetCopy, new LinearLayout.LayoutParams(0, -2, 1));
        targetButton = smallPill(TARGET_LABELS[selectedTarget] + "  ▾", false);
        targetButton.setOnClickListener(v -> showTargetPicker());
        targetRow.addView(targetButton);
        body.addView(targetRow);
        addGap(body, 13);

        LinearLayout exportRow = row();
        TextView pngButton = button("СОХРАНИТЬ PNG", ACCENT, BG, 12, true);
        pngButton.setOnClickListener(v -> launchPngExport());
        exportRow.addView(pngButton, weightedParams(1.1f, 54));
        View exportGap = new View(this);
        exportRow.addView(exportGap, new LinearLayout.LayoutParams(dp(9), 1));
        TextView packButton = button("ЭКСПОРТ .MCPACK", SURFACE_2, WHITE, 11, true);
        packButton.setOnClickListener(v -> launchPackExport());
        exportRow.addView(packButton, weightedParams(1, 54));
        body.addView(exportRow);
        addGap(body, 10);
        TextView exportNote = text("PNG — отдельный файл · .mcpack — готовый пакет для импорта в Minecraft", 9, MUTED, false);
        exportNote.setGravity(Gravity.CENTER);
        body.addView(exportNote);

        scroll.addView(body);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        showScreen(screen);
        updateEditorControls();
    }

    private View buildEditorHeader() {
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(19), dp(8), dp(19), dp(7));
        TextView back = button("‹", SURFACE_2, WHITE, 27, false);
        back.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        back.setOnClickListener(v -> renderHome());

        LinearLayout title = column();
        title.setPadding(dp(12), 0, 0, 0);
        TextView label = text("РЕДАКТОР", 9, ACCENT, true);
        label.setLetterSpacing(0.14f);
        TextView name = text(currentTexture.name, 17, WHITE, true);
        name.setMaxLines(1);
        title.addView(label);
        addGap(title, 2);
        title.addView(name);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextView fileBadge = text("PNG", 9, MUTED, true);
        fileBadge.setGravity(Gravity.CENTER);
        fileBadge.setPadding(dp(10), dp(7), dp(10), dp(7));
        fileBadge.setBackground(roundRect(0xFF162119, 0xFF28382D, 16, dp(1)));
        header.addView(fileBadge);
        return header;
    }

    private View buildPalette() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout items = row();
        items.setGravity(Gravity.CENTER_VERTICAL);
        items.setPadding(dp(1), dp(2), dp(1), dp(2));
        paletteButtons.clear();

        for (int i = 0; i < PALETTE.length; i++) {
            final int paletteColor = PALETTE[i];
            TextView swatch = new TextView(this);
            swatch.setGravity(Gravity.CENTER);
            swatch.setText(i == 0 ? "✓" : "");
            swatch.setTextColor(isLight(paletteColor) ? 0xFF172017 : WHITE);
            swatch.setTextSize(12);
            swatch.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            swatch.setBackground(swatchBackground(paletteColor, i == 0));
            swatch.setContentDescription("Выбрать цвет " + colorHex(paletteColor));
            swatch.setOnClickListener(v -> {
                selectedColor = paletteColor;
                selectedTool = TextureCanvasView.TOOL_BRUSH;
                editorCanvas.setColor(selectedColor);
                editorCanvas.setTool(selectedTool);
                updateEditorControls();
            });
            LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(dp(34), dp(34));
            swatchParams.rightMargin = dp(8);
            items.addView(swatch, swatchParams);
            paletteButtons.add(swatch);
        }

        TextView addColor = new TextView(this);
        addColor.setText("＋");
        addColor.setTextSize(20);
        addColor.setTextColor(ACCENT);
        addColor.setGravity(Gravity.CENTER);
        addColor.setBackground(roundRect(SURFACE_2, 0xFF39503C, 20, dp(1)));
        addColor.setContentDescription("Выбрать любой цвет");
        addColor.setOnClickListener(v -> showColorPicker());
        items.addView(addColor, new LinearLayout.LayoutParams(dp(34), dp(34)));
        scroll.addView(items);
        return scroll;
    }

    private View buildToolRow() {
        LinearLayout tools = row();
        tools.setGravity(Gravity.CENTER_VERTICAL);
        toolButtons.clear();
        String[] labels = new String[] { "✎\nКИСТЬ", "▧\nЗАЛИВКА", "⌕\nПИПЕТКА", "⌫\nЛАСТИК" };
        int[] toolsId = new int[] {
                TextureCanvasView.TOOL_BRUSH, TextureCanvasView.TOOL_FILL,
                TextureCanvasView.TOOL_PICKER, TextureCanvasView.TOOL_ERASER
        };
        for (int i = 0; i < labels.length; i++) {
            final int tool = toolsId[i];
            TextView item = new TextView(this);
            item.setText(labels[i]);
            item.setTextSize(10);
            item.setLineSpacing(dp(3), 1f);
            item.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(3), dp(5), dp(3), dp(5));
            item.setBackground(roundRect(SURFACE, 0xFF29382E, 13, dp(1)));
            item.setOnClickListener(v -> {
                selectedTool = tool;
                editorCanvas.setTool(tool);
                updateEditorControls();
            });
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(0, dp(58), 1);
            if (i > 0) itemParams.leftMargin = dp(7);
            tools.addView(item, itemParams);
            toolButtons.add(item);
        }
        return tools;
    }

    private void updateEditorControls() {
        if (editorCanvas == null) return;
        editorCanvas.setColor(selectedColor);
        editorCanvas.setTool(selectedTool);
        editorCanvas.setGridEnabled(gridEnabled);
        if (gridButton != null) {
            gridButton.setText(gridEnabled ? "СЕТКА  ВКЛ" : "СЕТКА  ВЫКЛ");
            gridButton.setBackground(roundRect(gridEnabled ? 0xFF1A2A1D : SURFACE_2,
                    gridEnabled ? 0xFF426A3C : 0xFF344038, 15, dp(1)));
            gridButton.setTextColor(gridEnabled ? ACCENT : MUTED);
        }
        if (colorValue != null) colorValue.setText(colorHex(selectedColor));
        if (targetButton != null) targetButton.setText(TARGET_LABELS[selectedTarget] + "  ▾");
        for (int i = 0; i < toolButtons.size(); i++) {
            TextView item = toolButtons.get(i);
            int tool = i;
            boolean active = tool == selectedTool;
            item.setTextColor(active ? ACCENT : MUTED);
            item.setBackground(roundRect(active ? 0xFF1A2A1D : SURFACE,
                    active ? 0xFF5A854B : 0xFF29382E, 13, dp(1)));
        }
        for (int i = 0; i < paletteButtons.size(); i++) {
            TextView item = paletteButtons.get(i);
            boolean active = PALETTE[i] == selectedColor;
            item.setText(active ? "✓" : "");
            item.setTextColor(isLight(PALETTE[i]) ? 0xFF172017 : WHITE);
            item.setBackground(swatchBackground(PALETTE[i], active));
        }
        updateHistoryButtons();
    }

    private void updateHistoryButtons() {
        if (currentTexture == null) return;
        if (undoButton != null) setUtilityEnabled(undoButton, currentTexture.canUndo());
        if (redoButton != null) setUtilityEnabled(redoButton, currentTexture.canRedo());
    }

    private void setUtilityEnabled(TextView button, boolean enabled) {
        button.setAlpha(enabled ? 1f : 0.42f);
        button.setEnabled(enabled);
    }

    private void undo() {
        if (currentTexture.undo()) {
            editorCanvas.invalidate();
            saveCurrentTexture();
            updateEditorControls();
        }
    }

    private void redo() {
        if (currentTexture.redo()) {
            editorCanvas.invalidate();
            saveCurrentTexture();
            updateEditorControls();
        }
    }

    private void showTargetPicker() {
        new AlertDialog.Builder(this)
                .setTitle("Какой блок заменить?")
                .setItems(TARGET_LABELS, (dialog, which) -> {
                    selectedTarget = which;
                    updateEditorControls();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // ---------------------------------------------------------------------
    // Color picker and new document dialog
    // ---------------------------------------------------------------------

    private void showNewTextureDialog() {
        String[] sizes = new String[] { "16 × 16 px  ·  классика", "32 × 32 px  ·  чётче", "64 × 64 px  ·  больше деталей" };
        new AlertDialog.Builder(this)
                .setTitle("Новая текстура")
                .setMessage("Выбери размер пиксельного полотна")
                .setItems(sizes, (dialog, which) -> {
                    int size = which == 0 ? 16 : which == 1 ? 32 : 64;
                    showTextureNameDialog(size);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showTextureNameDialog(int size) {
        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setTextColor(WHITE);
        nameInput.setHintTextColor(MUTED);
        nameInput.setHint("Например, Мой блок");
        nameInput.setText("Моя текстура");
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        int pad = dp(23);
        nameInput.setPadding(pad, dp(15), pad, dp(9));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Назови проект")
                .setView(nameInput)
                .setPositiveButton("Создать", (d, which) -> {
                    String name = nameInput.getText().toString().trim();
                    currentTexture = TextureDocument.blank(name.isEmpty() ? "Моя текстура" : name, size);
                    selectedColor = ACCENT;
                    selectedTool = TextureCanvasView.TOOL_BRUSH;
                    selectedTarget = 0;
                    saveCurrentTexture();
                    openEditor();
                })
                .setNegativeButton("Назад", null)
                .create();
        dialog.show();
        nameInput.requestFocus();
    }

    private void showColorPicker() {
        int[] hsv = new int[3];
        Color.colorToHSV(selectedColor, hsv);
        LinearLayout content = column();
        content.setPadding(dp(20), dp(5), dp(20), dp(10));
        TextView preview = new TextView(this);
        preview.setGravity(Gravity.CENTER);
        preview.setText("ПРЕДПРОСМОТР");
        preview.setTextSize(10);
        preview.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        preview.setTextColor(BG);
        preview.setBackground(roundRect(selectedColor, 0, 14, 0));
        content.addView(preview, new LinearLayout.LayoutParams(-1, dp(52)));
        addGap(content, 13);

        SeekBar hue = new SeekBar(this);
        SeekBar saturation = new SeekBar(this);
        SeekBar brightness = new SeekBar(this);
        hue.setMax(360);
        saturation.setMax(100);
        brightness.setMax(100);
        hue.setProgress(Math.round(hsv[0]));
        saturation.setProgress(Math.round(hsv[1] * 100));
        brightness.setProgress(Math.round(hsv[2] * 100));
        addSlider(content, "ОТТЕНОК", hue);
        addSlider(content, "НАСЫЩЕННОСТЬ", saturation);
        addSlider(content, "ЯРКОСТЬ", brightness);
        Runnable refresh = () -> {
            int value = Color.HSVToColor(new float[] {
                    hue.getProgress(), saturation.getProgress() / 100f, brightness.getProgress() / 100f
            });
            preview.setBackground(roundRect(value, 0, 14, 0));
            preview.setText(colorHex(value));
            preview.setTextColor(isLight(value) ? BG : WHITE);
            preview.setTag(value);
        };
        hue.setOnSeekBarChangeListener(seekListener(refresh));
        saturation.setOnSeekBarChangeListener(seekListener(refresh));
        brightness.setOnSeekBarChangeListener(seekListener(refresh));
        refresh.run();

        new AlertDialog.Builder(this)
                .setTitle("Свой цвет")
                .setView(content)
                .setPositiveButton("Выбрать", (dialog, which) -> {
                    Object colorTag = preview.getTag();
                    if (colorTag instanceof Integer) selectedColor = (Integer) colorTag;
                    selectedTool = TextureCanvasView.TOOL_BRUSH;
                    updateEditorControls();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private SeekBar.OnSeekBarChangeListener seekListener(Runnable refresh) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { refresh.run(); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        };
    }

    private void addSlider(LinearLayout parent, String label, SeekBar seekBar) {
        TextView caption = text(label, 9, MUTED, true);
        caption.setLetterSpacing(0.1f);
        parent.addView(caption);
        parent.addView(seekBar, new LinearLayout.LayoutParams(-1, dp(38)));
        addGap(parent, 4);
    }

    // ---------------------------------------------------------------------
    // Import, PNG export and Minecraft Bedrock .mcpack export
    // ---------------------------------------------------------------------

    private void launchImportPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "image/png", "image/*", "application/zip", "application/x-zip-compressed", "application/octet-stream"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_IMPORT);
        } catch (Exception error) {
            Toast.makeText(this, "Не удалось открыть выбор файлов", Toast.LENGTH_LONG).show();
        }
    }

    private void launchPngExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_TITLE, safeFileName(currentTexture.name) + ".png");
        try {
            startActivityForResult(intent, REQUEST_EXPORT_PNG);
        } catch (Exception error) {
            Toast.makeText(this, "Не удалось открыть сохранение файла", Toast.LENGTH_LONG).show();
        }
    }

    private void launchPackExport() {
        lastExportedPackName = safeFileName(currentTexture.name) + "_" + TARGET_KEYS[selectedTarget] + ".mcpack";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, lastExportedPackName);
        try {
            startActivityForResult(intent, REQUEST_EXPORT_PACK);
        } catch (Exception error) {
            Toast.makeText(this, "Не удалось открыть сохранение файла", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_IMPORT) {
            importDocument(uri);
        } else if (requestCode == REQUEST_EXPORT_PNG) {
            exportPng(uri);
        } else if (requestCode == REQUEST_EXPORT_PACK) {
            exportPack(uri);
        }
    }

    private void importDocument(Uri uri) {
        String displayName = queryDisplayName(uri);
        String lowerName = displayName.toLowerCase(Locale.ROOT);
        String type = getContentResolver().getType(uri);
        Bitmap bitmap = null;
        try {
            if (lowerName.endsWith(".mcpack") || lowerName.endsWith(".zip")
                    || (type != null && (type.contains("zip") || type.equals("application/octet-stream")))) {
                bitmap = findTextureInPack(uri);
            } else {
                try (InputStream stream = getContentResolver().openInputStream(uri)) {
                    if (stream != null) bitmap = decodeTexture(stream);
                }
            }
            if (bitmap == null) {
                Toast.makeText(this, "Не удалось найти PNG-текстуру в файле", Toast.LENGTH_LONG).show();
                return;
            }
            String textureName = displayName.replaceFirst("(?i)\\.(png|mcpack|zip)$", "").trim();
            if (textureName.isEmpty()) textureName = "Импортированная текстура";
            currentTexture = TextureDocument.fromBitmap(bitmap, textureName);
            bitmap = null;
            selectedColor = ACCENT;
            selectedTool = TextureCanvasView.TOOL_BRUSH;
            selectedTarget = 0;
            saveCurrentTexture();
            Toast.makeText(this, "Текстура загружена · " + currentTexture.size + " × " + currentTexture.size, Toast.LENGTH_SHORT).show();
            openEditor();
        } catch (Exception error) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            Toast.makeText(this, "Ошибка чтения файла: " + friendlyError(error), Toast.LENGTH_LONG).show();
        }
    }

    private Bitmap findTextureInPack(Uri uri) throws IOException {
        try (InputStream raw = getContentResolver().openInputStream(uri);
             ZipInputStream zip = raw == null ? null : new ZipInputStream(raw)) {
            if (zip == null) return null;
            Bitmap fallback = null;
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (!entry.isDirectory() && name.endsWith(".png") && !name.endsWith("pack_icon.png")) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = zip.read(buffer)) != -1) {
                        if (bytes.size() + count > 20 * 1024 * 1024) break;
                        bytes.write(buffer, 0, count);
                    }
                    Bitmap candidate = decodeTexture(bytes.toByteArray());
                    if (candidate != null) {
                        if (name.contains("textures/blocks/") || name.startsWith("textures/")) {
                            if (fallback != null && !fallback.isRecycled()) fallback.recycle();
                            return candidate;
                        }
                        if (fallback == null) fallback = candidate;
                        else candidate.recycle();
                    }
                }
                zip.closeEntry();
            }
            return fallback;
        }
    }

    private Bitmap decodeTexture(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (Math.max(bounds.outWidth / options.inSampleSize, bounds.outHeight / options.inSampleSize) > 512) {
            options.inSampleSize *= 2;
        }
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private Bitmap decodeTexture(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (bytes.size() + count > 24 * 1024 * 1024) throw new IOException("файл слишком большой");
            bytes.write(buffer, 0, count);
        }
        return decodeTexture(bytes.toByteArray());
    }

    private void exportPng(Uri uri) {
        try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
            if (output == null) throw new IOException("Не удалось открыть файл");
            Bitmap bitmap = currentTexture.toBitmap();
            boolean saved = bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            bitmap.recycle();
            if (!saved) throw new IOException("PNG не записан");
            Toast.makeText(this, "PNG сохранён в выбранную папку", Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            Toast.makeText(this, "Не удалось сохранить PNG: " + friendlyError(error), Toast.LENGTH_LONG).show();
        }
    }

    private void exportPack(Uri uri) {
        try {
            OutputStream output = getContentResolver().openOutputStream(uri, "w");
            if (output == null) throw new IOException("Не удалось открыть файл");
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                writeZipEntry(zip, "manifest.json", createManifest().getBytes(StandardCharsets.UTF_8));

                Bitmap textureBitmap = currentTexture.toBitmap();
                byte[] texturePng;
                try {
                    texturePng = bitmapBytes(textureBitmap);
                } finally {
                    if (!textureBitmap.isRecycled()) textureBitmap.recycle();
                }
                writeZipEntry(zip, "textures/blocks/" + TARGET_KEYS[selectedTarget] + ".png", texturePng);

                Bitmap icon = currentTexture.toBitmap();
                Bitmap scaledIcon = Bitmap.createScaledBitmap(icon, 64, 64, false);
                byte[] iconPng;
                try {
                    iconPng = bitmapBytes(scaledIcon);
                } finally {
                    if (scaledIcon != icon && !scaledIcon.isRecycled()) scaledIcon.recycle();
                    if (!icon.isRecycled()) icon.recycle();
                }
                writeZipEntry(zip, "pack_icon.png", iconPng);
                zip.finish();
            }
            showPackExportSuccess(uri);
        } catch (Exception error) {
            Toast.makeText(this, "Не удалось собрать .mcpack: " + friendlyError(error), Toast.LENGTH_LONG).show();
        }
    }

    private String createManifest() throws Exception {
        String packUuid = UUID.randomUUID().toString();
        String moduleUuid = UUID.randomUUID().toString();
        JSONObject root = new JSONObject();
        root.put("format_version", 2);
        JSONObject header = new JSONObject();
        header.put("name", "БлокЛаб — " + currentTexture.name);
        header.put("description", "Текстура для блока «" + TARGET_LABELS[selectedTarget] + "» · создано в БлокЛаб");
        header.put("uuid", packUuid);
        header.put("min_engine_version", new JSONArray().put(1).put(20).put(0));
        header.put("version", new JSONArray().put(1).put(0).put(0));
        root.put("header", header);
        JSONObject module = new JSONObject();
        module.put("type", "resources");
        module.put("uuid", moduleUuid);
        module.put("version", new JSONArray().put(1).put(0).put(0));
        root.put("modules", new JSONArray().put(module));
        return root.toString(2);
    }

    private byte[] bitmapBytes(Bitmap bitmap) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        boolean success = bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        if (!success) throw new IOException("Ошибка кодирования PNG");
        return output.toByteArray();
    }

    private void writeZipEntry(ZipOutputStream zip, String name, byte[] contents) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(contents);
        zip.closeEntry();
    }

    private void showPackExportSuccess(Uri uri) {
        new AlertDialog.Builder(this)
                .setTitle("Пакет готов")
                .setMessage(".mcpack сохранён. Нажми «Открыть», чтобы передать его в Minecraft, или найди файл в выбранной папке.")
                .setPositiveButton("ОТКРЫТЬ В MINECRAFT", (dialog, which) -> openPack(uri))
                .setNegativeButton("ГОТОВО", (dialog, which) -> Toast.makeText(this, "Пакет сохранён", Toast.LENGTH_SHORT).show())
                .show();
    }

    private void openPack(Uri uri) {
        Intent open = new Intent(Intent.ACTION_VIEW);
        open.setDataAndType(uri, "application/octet-stream");
        open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        open.setClipData(ClipData.newUri(getContentResolver(), "Minecraft resource pack", uri));
        try {
            startActivity(open);
        } catch (Exception missingHandler) {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/octet-stream");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            share.setClipData(ClipData.newUri(getContentResolver(), "Minecraft resource pack", uri));
            try {
                startActivity(Intent.createChooser(share, "Открыть пакет в…"));
            } catch (Exception error) {
                Toast.makeText(this, "Файл сохранён, но приложение для импорта не найдено", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ---------------------------------------------------------------------
    // Persistence and view helpers
    // ---------------------------------------------------------------------

    private TextureDocument loadSavedTexture() {
        try {
            String encoded = preferences.getString("texture_png", null);
            if (encoded == null) return null;
            byte[] bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) return null;
            return TextureDocument.fromBitmap(bitmap, preferences.getString("texture_name", "Моя текстура"));
        } catch (Exception error) {
            return null;
        }
    }

    private void saveCurrentTexture() {
        if (preferences == null || currentTexture == null) return;
        Bitmap bitmap = currentTexture.toBitmap();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        bitmap.recycle();
        preferences.edit()
                .putString("texture_name", currentTexture.name)
                .putString("texture_png", android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP))
                .apply();
    }

    private String queryDisplayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        String tail = uri.getLastPathSegment();
        return tail == null ? "texture.png" : tail;
    }

    private String safeFileName(String raw) {
        String value = Normalizer.normalize(raw == null ? "texture" : raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "_").replaceAll("^_+|_+$", "");
        return value.isEmpty() ? "blocklab_texture" : value;
    }

    private String friendlyError(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? "проверь доступ к файлу" : message;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private TextView text(String label, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        return view;
    }

    private TextView button(String label, int background, int foreground, float sizeSp, boolean bold) {
        TextView view = text(label, sizeSp, foreground, bold);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(9), dp(6), dp(9), dp(6));
        view.setBackground(roundRect(background, background == ACCENT ? 0 : 0xFF344038, 15, background == ACCENT ? 0 : dp(1)));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private TextView smallPill(String label, boolean active) {
        TextView view = text(label, 9, active ? ACCENT : WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), dp(7), dp(10), dp(7));
        view.setBackground(roundRect(active ? 0xFF1A2A1D : SURFACE_2,
                active ? 0xFF426A3C : 0xFF344038, 16, dp(1)));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private TextView utilityButton(String label) {
        TextView view = button(label, SURFACE_2, WHITE, 10, true);
        return view;
    }

    private GradientDrawable swatchBackground(int color, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        drawable.setStroke(dp(selected ? 3 : 1), selected ? WHITE : 0xFF405045);
        return drawable;
    }

    private GradientDrawable roundRect(int fill, int stroke, float radiusDp, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, stroke);
        return drawable;
    }

    private void addGap(LinearLayout parent, int heightDp) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private LinearLayout.LayoutParams weightedParams(float weight, int heightDp) {
        return new LinearLayout.LayoutParams(0, dp(heightDp), weight);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String colorHex(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    private boolean isLight(int color) {
        double brightness = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000.0;
        return brightness > 160;
    }

    @Override
    public void onBackPressed() {
        if (editorCanvas != null) {
            editorCanvas = null;
            toolButtons.clear();
            paletteButtons.clear();
            renderHome();
        } else {
            super.onBackPressed();
        }
    }

    private final class LogoMark extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        LogoMark(android.content.Context context) { super(context); }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            float side = Math.min(getWidth(), getHeight());
            float r = dp(11);
            paint.setColor(0xFF17231A);
            canvas.drawRoundRect(new RectF(0, 0, side, side), r, r, paint);
            float unit = side * 0.22f;
            float gap = side * 0.065f;
            float left = side * 0.22f;
            float top = side * 0.22f;
            int[] shades = new int[] { ACCENT, 0xFF64C45B, 0xFF3E8245, 0xFFCAF28C };
            int[][] cells = new int[][] { {0,0}, {1,0}, {0,1}, {1,1} };
            for (int i = 0; i < cells.length; i++) {
                float x = left + cells[i][0] * (unit + gap);
                float y = top + cells[i][1] * (unit + gap);
                paint.setColor(shades[i]);
                canvas.drawRect(x, y, x + unit, y + unit, paint);
            }
            paint.setColor(0xFF426B44);
            canvas.drawRect(side * 0.65f, side * 0.22f, side * 0.78f, side * 0.78f, paint);
        }
    }
}
