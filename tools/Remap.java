import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.SimpleRemapper;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;
import java.util.zip.*;

/**
 * Build tool for Mojang ProGuard mappings.
 *   deobf   <mappings> <obf.jar> <out-named.jar>
 *   genobf  <mappings> <out.java> <hook=owner.name(desc)>...
 *   verify  <Hooks.class> <hook=owner.name(desc)>...
 *   obf     <mappings> <named.jar> <in-classes-dir> <out-classes-dir>
 */
public class Remap {

    static final Map<String, String> n2o = new HashMap<>();
    static final Map<String, String> o2n = new HashMap<>();
    static final Map<String, Map<String, String>> nMethods = new HashMap<>();
    static final Map<String, Map<String, String>> nFields = new HashMap<>();
    static final Map<String, Map<String, String>> oMethods = new HashMap<>();
    static final Map<String, Map<String, String>> oFields = new HashMap<>();

    static final Pattern CLS = Pattern.compile("^(\\S+) -> (\\S+):$");
    static final Pattern MTH = Pattern.compile("^(?:\\d+:\\d+:)?(\\S+) ([^\\s(]+)\\(([^)]*)\\)(?::\\d+(?::\\d+)?)? -> (\\S+)$");
    static final Pattern FLD = Pattern.compile("^(\\S+) ([^\\s(]+) -> (\\S+)$");

    public static void main(String[] a) throws Exception {
        switch (a[0]) {
            case "deobf" -> { load(Path.of(a[1])); deobf(Path.of(a[2]), Path.of(a[3])); }
            case "genobf" -> { load(Path.of(a[1])); genobf(Path.of(a[2]), Arrays.copyOfRange(a, 3, a.length)); }
            case "verify" -> verify(Path.of(a[1]), Arrays.copyOfRange(a, 2, a.length));
            case "obf" -> { load(Path.of(a[1])); obf(Path.of(a[2]), Path.of(a[3]), Path.of(a[4])); }
            default -> throw new IllegalArgumentException(a[0]);
        }
    }

    static void load(Path file) throws IOException {
        record Member(String owner, String type, String name, String args, String obf, boolean method) {}
        List<Member> members = new ArrayList<>();
        String cur = null;
        for (String line : Files.readAllLines(file)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            if (!line.startsWith(" ")) {
                Matcher m = CLS.matcher(line);
                if (!m.matches()) throw new IllegalStateException("Bad class line: " + line);
                String named = m.group(1).replace('.', '/'), obf = m.group(2).replace('.', '/');
                n2o.put(named, obf);
                o2n.put(obf, named);
                cur = named;
                continue;
            }
            String t = line.trim();
            Matcher m = MTH.matcher(t);
            if (m.matches()) {
                String name = m.group(2);
                if (name.contains(".") || name.startsWith("<")) continue;
                members.add(new Member(cur, m.group(1), name, m.group(3), m.group(4), true));
                continue;
            }
            m = FLD.matcher(t);
            if (m.matches()) members.add(new Member(cur, m.group(1), m.group(2), null, m.group(3), false));
        }
        SimpleRemapper toObf = new SimpleRemapper(n2o);
        for (Member mb : members) {
            String oOwner = n2o.get(mb.owner);
            if (mb.method) {
                StringBuilder d = new StringBuilder("(");
                if (!mb.args.isEmpty()) for (String p : mb.args.split(",")) d.append(toDesc(p));
                String desc = d.append(')').append(toDesc(mb.type)).toString();
                nMethods.computeIfAbsent(mb.owner, k -> new HashMap<>()).put(mb.name + desc, mb.obf);
                oMethods.computeIfAbsent(oOwner, k -> new HashMap<>()).put(mb.obf + toObf.mapMethodDesc(desc), mb.name);
            } else {
                String desc = toDesc(mb.type);
                nFields.computeIfAbsent(mb.owner, k -> new HashMap<>()).put(mb.name + ":" + desc, mb.obf);
                oFields.computeIfAbsent(oOwner, k -> new HashMap<>()).put(mb.obf + ":" + toObf.mapDesc(desc), mb.name);
            }
        }
        System.out.println("Mappings: " + n2o.size() + " classes, " + members.size() + " members");
    }

    static String toDesc(String t) {
        int dims = 0;
        while (t.endsWith("[]")) { dims++; t = t.substring(0, t.length() - 2); }
        String base = switch (t) {
            case "void" -> "V"; case "boolean" -> "Z"; case "byte" -> "B"; case "char" -> "C";
            case "short" -> "S"; case "int" -> "I"; case "long" -> "J"; case "float" -> "F";
            case "double" -> "D";
            default -> "L" + t.replace('.', '/') + ";";
        };
        return "[".repeat(dims) + base;
    }

    static class MapRemapper extends Remapper {
        final Map<String, String> classes;
        final Map<String, Map<String, String>> methods, fields;
        final Map<String, String[]> parents;

        MapRemapper(Map<String, String> c, Map<String, Map<String, String>> m,
                    Map<String, Map<String, String>> f, Map<String, String[]> p) {
            classes = c; methods = m; fields = f; parents = p;
        }

        @Override public String map(String name) {
            String r = classes.get(name);
            if (r != null) return r;
            int i = name.lastIndexOf('$');
            if (i > 0) {
                String outer = map(name.substring(0, i));
                if (!outer.equals(name.substring(0, i))) return outer + name.substring(i);
            }
            return name;
        }

        @Override public String mapMethodName(String owner, String name, String desc) {
            if (name.startsWith("<") || owner.startsWith("[")) return name;
            String r = find(methods, owner, name + desc);
            return r != null ? r : name;
        }

        @Override public String mapFieldName(String owner, String name, String desc) {
            String r = find(fields, owner, name + ":" + desc);
            return r != null ? r : name;
        }

        @Override public String mapRecordComponentName(String owner, String name, String desc) {
            return mapFieldName(owner, name, desc);
        }

        String find(Map<String, Map<String, String>> members, String owner, String key) {
            Deque<String> q = new ArrayDeque<>();
            Set<String> seen = new HashSet<>();
            q.add(owner);
            while (!q.isEmpty()) {
                String c = q.poll();
                if (!seen.add(c)) continue;
                Map<String, String> m = members.get(c);
                if (m != null) {
                    String r = m.get(key);
                    if (r != null) return r;
                }
                String[] p = parents.get(c);
                if (p != null) for (String s : p) if (s != null) q.add(s);
            }
            return null;
        }
    }

    static void addParents(Map<String, String[]> parents, byte[] cls) {
        ClassReader cr = new ClassReader(cls);
        String[] itf = cr.getInterfaces();
        String[] p = new String[itf.length + 1];
        p[0] = cr.getSuperName();
        System.arraycopy(itf, 0, p, 1, itf.length);
        parents.put(cr.getClassName(), p);
    }

    static Map<String, byte[]> readJarClasses(Path jar) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                String n = e.getName();
                if (n.endsWith(".class") && !n.startsWith("META-INF/") && !n.endsWith("module-info.class"))
                    out.put(n, zin.readAllBytes());
            }
        }
        return out;
    }

    static byte[] remap(byte[] cls, Remapper r) {
        ClassReader cr = new ClassReader(cls);
        ClassWriter cw = new ClassWriter(0);
        cr.accept(new ClassRemapper(cw, r), 0);
        return cw.toByteArray();
    }

    static void deobf(Path obfJar, Path outJar) throws IOException {
        Map<String, byte[]> classes = readJarClasses(obfJar);
        Map<String, String[]> parents = new HashMap<>();
        for (byte[] b : classes.values()) addParents(parents, b);
        MapRemapper r = new MapRemapper(o2n, oMethods, oFields, parents);
        try (ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(outJar))) {
            for (byte[] b : classes.values()) {
                String name = new ClassReader(b).getClassName();
                zout.putNextEntry(new ZipEntry(r.map(name) + ".class"));
                zout.write(remap(b, r));
                zout.closeEntry();
            }
        }
        System.out.println("Deobfuscated " + classes.size() + " classes -> " + outJar);
    }

    record Spec(String hook, String owner, String name, String desc) {
        static Spec parse(String s) {
            int eq = s.indexOf('='), par = s.indexOf('('), dot = s.lastIndexOf('.', par);
            return new Spec(s.substring(0, eq), s.substring(eq + 1, dot), s.substring(dot + 1, par), s.substring(par));
        }
    }

    static void genobf(Path out, String[] specs) throws IOException {
        SimpleRemapper toObf = new SimpleRemapper(n2o);
        StringBuilder sb = new StringBuilder("package com.modloader;\n\npublic final class ObfNames {\n");
        boolean ok = true;
        for (String s : specs) {
            Spec sp = Spec.parse(s);
            String oOwner = n2o.get(sp.owner);
            Map<String, String> m = nMethods.getOrDefault(sp.owner, Map.of());
            String oName = m.get(sp.name + sp.desc);
            if (oOwner == null || oName == null) {
                ok = false;
                System.out.println("ERROR: hook target not found: " + sp.owner + "." + sp.name + sp.desc);
                System.out.println("  class mapped: " + (oOwner != null));
                m.keySet().stream().filter(k -> k.startsWith(sp.name + "(")).sorted()
                    .forEach(k -> System.out.println("  candidate: " + sp.name + k.substring(sp.name.length())));
                continue;
            }
            String oDesc = toObf.mapMethodDesc(sp.desc);
            System.out.println("Hook " + sp.hook + ": " + sp.owner + "." + sp.name + sp.desc + " -> " + oOwner + "." + oName + oDesc);
            sb.append("    public static final String ").append(sp.hook).append("_OWNER = \"").append(oOwner).append("\";\n");
            sb.append("    public static final String ").append(sp.hook).append("_NAME = \"").append(oName).append("\";\n");
            sb.append("    public static final String ").append(sp.hook).append("_DESC = \"").append(oDesc).append("\";\n");
        }
        if (!ok) System.exit(2);
        sb.append("}\n");
        Files.createDirectories(out.getParent());
        Files.writeString(out, sb.toString());
    }

    static void verify(Path hooksClass, String[] specs) throws IOException {
        Set<String> statics = new HashSet<>();
        new ClassReader(Files.readAllBytes(hooksClass)).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int acc, String n, String d, String s, String[] e) {
                if ((acc & Opcodes.ACC_STATIC) != 0 && (acc & Opcodes.ACC_PUBLIC) != 0) statics.add(n + d);
                return null;
            }
        }, ClassReader.SKIP_CODE);
        boolean ok = true;
        for (String s : specs) {
            Spec sp = Spec.parse(s);
            if (!statics.contains(sp.hook + sp.desc)) {
                ok = false;
                System.out.println("ERROR: Hooks is missing: public static " + sp.hook + sp.desc);
            }
        }
        if (!ok) System.exit(3);
        System.out.println("Hooks verified");
    }

    static void obf(Path namedJar, Path inDir, Path outDir) throws IOException {
        Map<String, String[]> parents = new HashMap<>();
        for (byte[] b : readJarClasses(namedJar).values()) addParents(parents, b);
        List<Path> files;
        try (Stream<Path> st = Files.walk(inDir)) {
            files = st.filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList());
        }
        Map<Path, byte[]> own = new LinkedHashMap<>();
        for (Path p : files) {
            byte[] b = Files.readAllBytes(p);
            own.put(p, b);
            addParents(parents, b);
        }
        MapRemapper r = new MapRemapper(n2o, nMethods, nFields, parents);
        for (Map.Entry<Path, byte[]> e : own.entrySet()) {
            Path out = outDir.resolve(inDir.relativize(e.getKey()));
            Files.createDirectories(out.getParent());
            Files.write(out, remap(e.getValue(), r));
        }
        System.out.println("Obfuscated " + own.size() + " classes -> " + outDir);
    }
}
