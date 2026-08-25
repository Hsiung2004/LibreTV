package com.bearfamily.app.bearontime;

import java.io.File;

public final class VoiceModule {
    public final String id;
    public final String name;
    public final String author;
    public final String version;
    public final String preview;
    public final boolean builtin;
    public final File directory;

    VoiceModule(String id, String name, String author, String version, String preview, boolean builtin, File directory) {
        this.id = id;
        this.name = name;
        this.author = author;
        this.version = version;
        this.preview = preview;
        this.builtin = builtin;
        this.directory = directory;
    }
}
