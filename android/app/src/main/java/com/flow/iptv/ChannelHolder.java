package com.flow.iptv;

import java.util.ArrayList;
import java.util.List;

/** Static holder to pass a (potentially large) channel list between activities
 *  without serializing it through an Intent (which has a ~1 MB limit). */
public final class ChannelHolder {
    private static final List<Channel> CURRENT = new ArrayList<>();
    private ChannelHolder() {}
    public static synchronized void set(List<Channel> list) {
        CURRENT.clear();
        if (list != null) CURRENT.addAll(list);
    }
    public static synchronized List<Channel> get() { return new ArrayList<>(CURRENT); }
}
