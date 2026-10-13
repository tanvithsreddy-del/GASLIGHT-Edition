package dev.kondalabs.gaslight.provider;

import java.util.UUID;

final class SceneVisibility {
    static final String PREFIX="gaslight-game:";
    record Identity(UUID session,long generation) {}
    static volatile Identity selected;
    static volatile boolean acknowledged;
    static Identity parse(String tag) {
        if(tag==null||!tag.startsWith(PREFIX))return null;
        try{var parts=tag.substring(PREFIX.length()).split(":");return new Identity(UUID.fromString(parts[0]),Long.parseLong(parts[1]));}
        catch(RuntimeException malformed){return null;}
    }
    static boolean visible(String tag){Identity id=parse(tag);return id!=null&&id.equals(selected);}
    static boolean interactive(String tag){return acknowledged&&visible(tag);}
    static void clear(){selected=null;acknowledged=false;}
}
