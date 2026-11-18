package com.muzdai.qobuzbridge;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class QueueItemsTest {
    @Test public void tracksBecomeMediaQueueItemArguments() throws Exception {
        String json = "[{\"id\":64868955,\"title\":\"Come Together\",\"version\":\"2019 Mix\",\"album\":\"Abbey Road\","
                + "\"albumId\":\"trrcz9pvaaz6b\",\"artist\":\"The Beatles\",\"duration\":259,\"explicit\":false},"
                + "{\"title\":\"no id\"},{\"id\":\"30369901\"}]";
        List<Object[]> items = QueueItems.fromJson(json, "come together");
        assertEquals(2, items.size());
        Object[] a = items.get(0);
        assertEquals(QueueItems.ARGS, a.length);
        assertNotNull(a[0]);                                   // uuid
        assertNull(a[1]);                                      // remoteId
        assertEquals("64868955", a[2]);                        // trackId
        assertEquals(items.get(1)[3], a[3]);                   // one tracking context for the list
        assertEquals("Come Together (2019 Mix)", a[5]);
        assertEquals("Abbey Road", a[6]);
        assertEquals("trrcz9pvaaz6b", a[7]);
        assertEquals("The Beatles", a[9]);
        assertEquals(259000L, a[12]);
        assertEquals(Boolean.TRUE, a[19]);                     // playable
        JSONObject source = new JSONObject((String) a[21]);
        assertEquals("64868955", source.getString("content_group_id"));
        assertEquals("come together", source.getJSONObject("tracking_path").getString("value"));
        assertEquals("30369901", items.get(1)[2]);
        assertTrue(items.get(1)[5] == null);
    }

    @Test public void versionInBrackets() {
        assertEquals("A (Live)", QueueItems.withVersion("A", "Live"));
        assertEquals("A", QueueItems.withVersion("A", ""));
        assertNull(QueueItems.withVersion(null, "Live"));
    }
}
