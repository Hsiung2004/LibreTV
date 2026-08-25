package com.bearfamily.app.bearontime;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

public final class WorkdayStore {
    private final android.content.SharedPreferences prefs;
    public WorkdayStore(Context c) { prefs = c.getSharedPreferences("bearontime_workdays", Context.MODE_PRIVATE); }

    public Set<String> holidays() { return new HashSet<>(prefs.getStringSet("holidays", new HashSet<>())); }
    public Set<String> workingDays() { return new HashSet<>(prefs.getStringSet("working_days", new HashSet<>())); }

    public void markHoliday(String date) { Set<String> h = holidays(), w = workingDays(); h.add(date); w.remove(date); save(h,w); }
    public void markWorkingDay(String date) { Set<String> h = holidays(), w = workingDays(); w.add(date); h.remove(date); save(h,w); }
    public void clear(String date) { Set<String> h = holidays(), w = workingDays(); h.remove(date); w.remove(date); save(h,w); }

    public String status(String date) {
        if (holidays().contains(date)) return "休假日（手動指定）";
        if (workingDays().contains(date)) return "工作日（手動指定）";
        return "依星期自動判定";
    }

    public String exportJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("holidays", new JSONArray(holidays()));
            o.put("working_days", new JSONArray(workingDays()));
        } catch (Exception ignored) {}
        return o.toString();
    }

    public void importJson(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        Set<String> h = arraySet(o.optJSONArray("holidays"));
        Set<String> w = arraySet(o.optJSONArray("working_days"));
        save(h,w);
    }

    private Set<String> arraySet(JSONArray a) {
        Set<String> s = new HashSet<>();
        if (a != null) for (int i=0;i<a.length();i++) { String v=a.optString(i,""); if (!v.trim().isEmpty()) s.add(v); }
        return s;
    }

    private void save(Set<String> h, Set<String> w) {
        prefs.edit().putStringSet("holidays", new HashSet<>(h)).putStringSet("working_days", new HashSet<>(w)).apply();
    }
}
