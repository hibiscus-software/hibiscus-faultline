/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.build;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Every tool's option values. Stored as {@code tool.option=true} entries so options added in later versions pick up
 * their defaults instead of being read as off.
 */
public final class BuildOptions {
    private final Map<BuildTool, Integer> masks = new EnumMap<>(BuildTool.class);

    public BuildOptions() {
        for (BuildTool tool : BuildTool.values()) {
            int mask = 0;
            for (BuildOption option : tool.options()) {
                if (option.defaultValue()) {
                    mask |= bit(option);
                }
            }
            masks.put(tool, mask);
        }
    }

    public boolean get(BuildTool tool, BuildOption option) {
        return (masks.get(tool) & bit(option)) != 0;
    }

    public void set(BuildTool tool, BuildOption option, boolean value) {
        if (!tool.options().contains(option)) {
            return;
        }
        int mask = masks.get(tool);
        masks.put(tool, value ? mask | bit(option) : mask & ~bit(option));
    }

    /** {@return the tool's values as a bit mask over {@link BuildOption} ordinals, for sending to the server} */
    public int mask(BuildTool tool) {
        return masks.get(tool);
    }

    /** Reads entries written by {@link #write()}; anything unknown or malformed is ignored. */
    public static BuildOptions read(List<? extends String> entries) {
        BuildOptions options = new BuildOptions();
        for (String entry : entries) {
            int dot = entry.indexOf('.');
            int equals = entry.indexOf('=');
            if (dot < 0 || equals < dot) {
                continue;
            }
            BuildTool tool = find(BuildTool.values(), entry.substring(0, dot));
            BuildOption option = find(BuildOption.values(), entry.substring(dot + 1, equals));
            if (tool != null && option != null) {
                options.set(tool, option, Boolean.parseBoolean(entry.substring(equals + 1)));
            }
        }
        return options;
    }

    public List<String> write() {
        List<String> entries = new ArrayList<>();
        for (BuildTool tool : BuildTool.values()) {
            for (BuildOption option : tool.options()) {
                entries.add(tool.id() + "." + option.id() + "=" + get(tool, option));
            }
        }
        return entries;
    }

    private static int bit(BuildOption option) {
        return 1 << option.ordinal();
    }

    private static <E extends Enum<E>> @Nullable E find(E[] values, String id) {
        for (E value : values) {
            if (value.name().equalsIgnoreCase(id)) {
                return value;
            }
        }
        return null;
    }
}
