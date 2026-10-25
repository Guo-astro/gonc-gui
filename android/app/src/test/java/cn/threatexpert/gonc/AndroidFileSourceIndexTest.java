package cn.threatexpert.gonc;

import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class AndroidFileSourceIndexTest {
    @Test public void generatedAliasesCannotCollideWithLiteralSuffixedNames() throws Exception {
        Method unique = AndroidFileSource.class.getDeclaredMethod("uniqueName", String.class, Map.class);
        unique.setAccessible(true);
        Map<String, Integer> used = new HashMap<>();
        Set<String> results = new HashSet<>();
        for (String name : new String[]{"photo.jpg", "photo.jpg", "photo-2.jpg", "photo.jpg", "photo-3.jpg"}) {
            assertTrue(results.add((String) unique.invoke(null, name, used)));
        }
        assertEquals(5, results.size());
    }

    @Test public void literalSuffixBeforeDuplicateAlsoStaysUnique() throws Exception {
        Method unique = AndroidFileSource.class.getDeclaredMethod("uniqueName", String.class, Map.class);
        unique.setAccessible(true);
        Map<String, Integer> used = new HashMap<>();
        assertEquals("a-2.jpg", unique.invoke(null, "a-2.jpg", used));
        assertEquals("a.jpg", unique.invoke(null, "a.jpg", used));
        assertEquals("a-3.jpg", unique.invoke(null, "a.jpg", used));
    }

    @Test public void sourceUsesIndexButExplicitListingAndMetadataStayFresh() throws Exception {
        String source = source();
        assertTrue(source.contains("current = current.index.find(parts[i], refresh)"));
        assertFalse(source.contains("for (Node child : current.children())"));
        assertTrue(source.contains("for (Node child : node.refreshChildren())"));
        assertTrue(source.contains("return index.refresh()"));
        assertTrue(source.contains("freshMetadata(node).toJson()"));
        assertTrue(source.contains("resolver.query(queryUri, null, null, null, null)"));
        assertTrue(source.contains("!node.documentName.equals(cursor.getString(nameColumn))"));
    }

    @Test public void sourceBoundsRecoveryAndReleasesIndexesOnStop() throws Exception {
        String source = source();
        assertTrue(source.contains("attempt < 2"));
        assertTrue(source.contains("resolve(name, attempt > 0)"));
        assertTrue(source.contains("if (closed) return null"));
        int closeAll = source.indexOf("void closeAll()");
        int resolve = source.indexOf("private Node resolve", closeAll);
        String cleanup = source.substring(closeAll, resolve);
        assertTrue(cleanup.contains("closed = true"));
        assertTrue(cleanup.contains("roots.clear()"));
        assertTrue(source.contains("if (!closed) roots.addAll(updated)"));
    }

    private static String source() throws Exception {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/cn/threatexpert/gonc/AndroidFileSource.java")), StandardCharsets.UTF_8);
    }
}
