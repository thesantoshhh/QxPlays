package com.qxplays.player;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Search across the whole library. */
public class SearchView extends LinearLayout implements LibraryData.Listener {

    private final MainActivity act;
    private final EditText input;
    private final ListView list;
    private final TextView count;
    private final List<MediaItem> results = new ArrayList<>();

    public SearchView(Context c) {
        super(c);
        act = (MainActivity) c;
        setOrientation(VERTICAL);
        setBackgroundColor(C.bg());
        addView(Browsers.base(act, "Search", null, null));

        LinearLayout bar = new LinearLayout(act);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(act, 16), Ui.dp(act, 6), Ui.dp(act, 16), Ui.dp(act, 6));
        bar.addView(Ui.icon(act, R.drawable.ic_search, 20, C.textDim()));
        bar.addView(Ui.space(act, 10));
        input = new EditText(act);
        input.setHint("Search videos and music…");
        input.setHintTextColor(C.textDim());
        input.setTextColor(C.text());
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        input.setPadding(0, Ui.dp(act, 8), 0, Ui.dp(act, 8));
        bar.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { runQuery(); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        Ui.setBg(bar, Ui.rect(Ui.dp(act, 14), C.surface()));
        LinearLayout barPad = new LinearLayout(act);
        barPad.setPadding(Ui.dp(act, 14), Ui.dp(act, 8), Ui.dp(act, 14), Ui.dp(act, 4));
        barPad.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(barPad);

        count = Ui.tv(act, "", 13, C.textDim(), 0);
        count.setPadding(Ui.dp(act, 18), Ui.dp(act, 4), Ui.dp(act, 18), Ui.dp(act, 2));
        addView(count);

        list = new ListView(act);
        list.setDivider(null);
        list.setSelector(android.R.color.transparent);
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return results.size(); }
            @Override public Object getItem(int position) { return results.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                MediaItem item = results.get(position);
                View row = MediaLists.listRow(act, item, false, true,
                        (MediaLists.Bridge) act, null, null);
                row.setOnClickListener(v -> act.play(new ArrayList<>(results), position));
                return row;
            }
        });
        addView(list, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LibraryData.subscribe(this);
        input.postDelayed(() -> {
            input.requestFocus();
            try {
                InputMethodManager imm = (InputMethodManager) act.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            } catch (Exception ignored) {}
        }, 200);
    }

    private void runQuery() {
        String q = input.getText().toString().trim().toLowerCase(Locale.US);
        results.clear();
        if (q.isEmpty()) {
            count.setText("");
            ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
            return;
        }
        for (MediaItem it : LibraryData.videos) if (it.name.toLowerCase(Locale.US).contains(q)) results.add(it);
        for (MediaItem it : LibraryData.audio) if (it.name.toLowerCase(Locale.US).contains(q)) results.add(it);
        Library.sort(results, 2);
        count.setText(results.size() + " result(s)");
        ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
    }

    @Override public void onLibraryChanged() { runQuery(); }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        LibraryData.unsubscribe(this);
        try {
            InputMethodManager imm = (InputMethodManager) act.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(getWindowToken(), 0);
        } catch (Exception ignored) {}
    }
}
