package com.bearfamily.app.bearontime;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class PlannerStore {
    private final File file;
    public PlannerStore(Context c) { file = new File(c.getFilesDir(), "BearOnTime/planner.json"); }

    public synchronized List<PlannerItem> all() {
        List<PlannerItem> out = new ArrayList<>();
        try {
            if (!file.isFile()) return out;
            JSONArray a = new JSONArray(VoiceModuleManager.readUtf8(file));
            for (int i = 0; i < a.length(); i++) out.add(PlannerItem.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) {}
        out.sort(Comparator.comparing((PlannerItem p) -> p.date).thenComparing(p -> p.done));
        return out;
    }

    public List<PlannerItem> forDate(String date) {
        List<PlannerItem> out = new ArrayList<>();
        for (PlannerItem p : all()) if (date.equals(p.date)) out.add(p);
        return out;
    }

    public synchronized void upsert(PlannerItem item) {
        List<PlannerItem> list = all();
        if (item.id == null || item.id.trim().isEmpty()) item.id = UUID.randomUUID().toString();
        boolean found = false;
        for (int i = 0; i < list.size(); i++) if (item.id.equals(list.get(i).id)) { list.set(i, item); found = true; break; }
        if (!found) list.add(item);
        save(list);
    }

    public synchronized void delete(String id) {
        List<PlannerItem> list = all();
        list.removeIf(p -> id.equals(p.id));
        save(list);
    }

    public String exportJson() {
        JSONArray a = new JSONArray();
        for (PlannerItem p : all()) a.put(p.toJson());
        return a.toString();
    }

    public void importJson(String json) throws Exception {
        JSONArray a = new JSONArray(json);
        List<PlannerItem> list = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) list.add(PlannerItem.fromJson(a.getJSONObject(i)));
        save(list);
    }

    private void save(List<PlannerItem> list) {
        JSONArray a = new JSONArray();
        for (PlannerItem p : list) a.put(p.toJson());
        try { VoiceModuleManager.writeUtf8(file, a.toString()); } catch (Exception ignored) {}
    }
}
