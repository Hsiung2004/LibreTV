package com.bearfamily.app.bearontime;

import org.json.JSONObject;

public final class PlannerItem {
    public String id;
    public String date;
    public String title;
    public String priority;
    public String notes;
    public boolean done;

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id); o.put("date", date); o.put("title", title);
            o.put("priority", priority); o.put("notes", notes); o.put("done", done);
        } catch (Exception ignored) {}
        return o;
    }

    public static PlannerItem fromJson(JSONObject o) {
        PlannerItem p = new PlannerItem();
        p.id = o.optString("id", java.util.UUID.randomUUID().toString());
        p.date = o.optString("date", "");
        p.title = o.optString("title", "待辦事項");
        p.priority = o.optString("priority", "普通");
        p.notes = o.optString("notes", "");
        p.done = o.optBoolean("done", false);
        return p;
    }
}
