package com.bearfamily.app.bearontime;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

public final class SettingsStore {
    private static final String PREFS = "bearontime_settings";
    private final SharedPreferences prefs;

    public SettingsStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isEnabled() { return prefs.getBoolean("enabled", false); }
    public void setEnabled(boolean v) { prefs.edit().putBoolean("enabled", v).apply(); }

    public int getAppVolume() { return prefs.getInt("app_volume", 80); }
    public void setAppVolume(int v) { prefs.edit().putInt("app_volume", Math.max(0, Math.min(100, v))).apply(); }

    public boolean isForceAlarmChannel() { return prefs.getBoolean("force_alarm_channel", true); }
    public void setForceAlarmChannel(boolean v) { prefs.edit().putBoolean("force_alarm_channel", v).apply(); }

    public boolean isRaiseAlarmVolume() { return prefs.getBoolean("raise_alarm_volume", true); }
    public void setRaiseAlarmVolume(boolean v) { prefs.edit().putBoolean("raise_alarm_volume", v).apply(); }

    public boolean isSkipHolidays() { return prefs.getBoolean("skip_holidays", false); }
    public void setSkipHolidays(boolean v) { prefs.edit().putBoolean("skip_holidays", v).apply(); }

    public boolean isHalfHourEnabled() { return prefs.getBoolean("half_hour_enabled", false); }
    public void setHalfHourEnabled(boolean v) { prefs.edit().putBoolean("half_hour_enabled", v).apply(); }

    public String getHalfHourTone() { return prefs.getString("half_hour_tone", "chime"); }
    public void setHalfHourTone(String v) { prefs.edit().putString("half_hour_tone", v == null ? "chime" : v).apply(); }

    public String getSelectedVoiceModuleId() { return prefs.getString("voice_module", "builtin_system_tts"); }
    public void setSelectedVoiceModuleId(String v) { prefs.edit().putString("voice_module", v == null ? "builtin_system_tts" : v).apply(); }

    public String getThemeKey() { return prefs.getString("theme", "sky_blue"); }
    public void setThemeKey(String v) { prefs.edit().putString("theme", v == null ? "sky_blue" : v).apply(); }

    public Set<Integer> getEnabledHours() {
        String raw = prefs.getString("enabled_hours", "");
        Set<Integer> out = new HashSet<>();
        if (raw == null || raw.trim().isEmpty()) {
            for (int i = 0; i < 24; i++) out.add(i);
            return out;
        }
        for (String s : raw.split(",")) {
            try {
                int h = Integer.parseInt(s.trim());
                if (h >= 0 && h <= 23) out.add(h);
            } catch (Exception ignored) {}
        }
        return out;
    }

    public void setEnabledHours(Set<Integer> hours) {
        StringBuilder sb = new StringBuilder();
        for (int h = 0; h < 24; h++) {
            if (hours != null && hours.contains(h)) {
                if (sb.length() > 0) sb.append(',');
                sb.append(h);
            }
        }
        prefs.edit().putString("enabled_hours", sb.toString()).apply();
    }

    public JSONObject toJsonObject() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("app", Brand.APP_ID);
            obj.put("backup_version", 1);
            obj.put("enabled", isEnabled());
            obj.put("app_volume", getAppVolume());
            obj.put("force_alarm_channel", isForceAlarmChannel());
            obj.put("raise_alarm_volume", isRaiseAlarmVolume());
            obj.put("skip_holidays", isSkipHolidays());
            obj.put("half_hour_enabled", isHalfHourEnabled());
            obj.put("half_hour_tone", getHalfHourTone());
            obj.put("voice_module", getSelectedVoiceModuleId());
            obj.put("theme", getThemeKey());
            JSONArray hours = new JSONArray();
            for (int h = 0; h < 24; h++) if (getEnabledHours().contains(h)) hours.put(h);
            obj.put("enabled_hours", hours);
        } catch (Exception ignored) {}
        return obj;
    }

    public String toJson() { return toJsonObject().toString(); }

    public void importJson(String json) throws Exception {
        JSONObject obj = new JSONObject(json);
        setEnabled(obj.optBoolean("enabled", isEnabled()));
        setAppVolume(obj.optInt("app_volume", getAppVolume()));
        setForceAlarmChannel(obj.optBoolean("force_alarm_channel", isForceAlarmChannel()));
        setRaiseAlarmVolume(obj.optBoolean("raise_alarm_volume", isRaiseAlarmVolume()));
        setSkipHolidays(obj.optBoolean("skip_holidays", isSkipHolidays()));
        setHalfHourEnabled(obj.optBoolean("half_hour_enabled", isHalfHourEnabled()));
        setHalfHourTone(obj.optString("half_hour_tone", getHalfHourTone()));
        setSelectedVoiceModuleId(obj.optString("voice_module", getSelectedVoiceModuleId()));
        setThemeKey(obj.optString("theme", getThemeKey()));
        JSONArray arr = obj.optJSONArray("enabled_hours");
        if (arr != null) {
            Set<Integer> hours = new HashSet<>();
            for (int i = 0; i < arr.length(); i++) {
                int h = arr.optInt(i, -1);
                if (h >= 0 && h < 24) hours.add(h);
            }
            setEnabledHours(hours);
        }
    }
}
