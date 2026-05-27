package com.flow.iptv;

import java.io.Serializable;

public class Channel implements Serializable {
    public final String name;
    public final String group;
    public final String url;
    public final String logo;
    public final String tvgId;
    public Channel(String name, String group, String url, String logo, String tvgId) {
        this.name = name; this.group = group; this.url = url; this.logo = logo; this.tvgId = tvgId;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Channel)) return false;
        Channel c = (Channel) o;
        return java.util.Objects.equals(url, c.url)
            && java.util.Objects.equals(name, c.name);
    }
    @Override public int hashCode() { return java.util.Objects.hash(url, name); }
}