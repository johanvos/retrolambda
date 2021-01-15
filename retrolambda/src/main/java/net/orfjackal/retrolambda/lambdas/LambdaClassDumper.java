// Copyright © 2013-2020 Esko Luontola and other Retrolambda contributors
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

package net.orfjackal.retrolambda.lambdas;

import net.orfjackal.retrolambda.fs.*;

import java.io.*;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.spi.FileSystemProvider;
import java.util.Set;

public class LambdaClassDumper implements AutoCloseable {

    private final LambdaClassSaver lambdaClassSaver;
    private Field dumperField;

    public LambdaClassDumper(LambdaClassSaver lambdaClassSaver) {
        this.lambdaClassSaver = lambdaClassSaver;
        // System.err.println("[RRLL] created LCD: "+this+" with saver = "+this.lambdaClassSaver);
    }

    public void install() {
        try {
            Class<?> mf = Class.forName("java.lang.invoke.InnerClassLambdaMetafactory");
            dumperField = mf.getDeclaredField("dumper");
            makeNonFinal(dumperField);
            dumperField.setAccessible(true);

            Path p = new VirtualPath("");
            Object npcd = newProxyClassesDumper(p);
            dumperField.set(null, npcd);
            // System.err.println("[RRLL] LCD, install saver = "+lambdaClassSaver+", npcd = "+npcd);
        } catch (Exception e) {
            System.err.println("ERROR INSTALL!");
            e.printStackTrace();
            throw new IllegalStateException("Cannot initialize dumper; unexpected JDK implementation. " +
                    "Please run Retrolambda using the Java agent (enable forking in the Maven plugin).", e);
        }
    }

    public void uninstall() {
        // System.err.println("[RRLL] LCD, uninstall saver = "+lambdaClassSaver);

        if (dumperField != null) {
            try {
                dumperField.set(null, null);
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void close() {
        uninstall();
    }

    private static void makeNonFinal(Field field) throws Exception {
        try {
            Field modifiers = field.getClass().getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            int mod = modifiers.getInt(field);
            modifiers.setInt(field, mod & ~Modifier.FINAL);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to make a field non-final (" + field + "). " +
                    "This known to fail on Java 12 and newer. Prefer using Java 8 or try using the Java agent " +
                    "(fork=true in the Maven plugin).", e);
        }
    }

    private static Object newProxyClassesDumper(Path dumpDir) throws Exception {
        Class<?> dumper = Class.forName("java.lang.invoke.ProxyClassesDumper");
        Constructor<?> c = dumper.getDeclaredConstructor(Path.class);
        c.setAccessible(true);
        Object answer = c.newInstance(dumpDir);
        System.err.println("[LCD] newProxyClassesDumper created for path "
                +dumpDir+", answer = "+answer);
        return answer;
    }


    private final class VirtualFSProvider extends FakeFileSystemProvider {

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) {
            // System.err.println("[RRLL] VFSP newByteChannel needed for path "+path+", saver = "+lambdaClassSaver);
            return new ClassChannel(path);
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) {
            // System.err.println("[RRLL] VFSP need to create dir "+dir);
        }

        @Override
        public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
            OutputStream answer = super.newOutputStream(path, options); //To change body of generated methods, choose Tools | Templates.
            // System.err.println("[RRLL] VFSP, newOutputStream asked for path = "+path+", answer = "+answer);

            return answer;
        }
        
        
    }

    private final class VirtualFS extends FakeFileSystem {

        @Override
        public FileSystemProvider provider() {
            // System.err.println("[RRLL] VFS, provider asked, saver = "+lambdaClassSaver);
            return new VirtualFSProvider();
        }
    }

    private final class VirtualPath extends FakePath {

        private final String path;

        public VirtualPath(String path) {
            this.path = path;
            // System.err.println("[RRLL] VP created for path = "+path+" and saver = "+lambdaClassSaver+", result = "+super.toString());
        }

        @Override
        public FileSystem getFileSystem() {
            return new VirtualFS();
        }

        @Override
        public Path getParent() {
            return this;
        }

        @Override
        public Path resolve(String other) {
            Thread.dumpStack();
            System.err.println("[LCD] VP "+super.toString()+" needs to resolve "+other+", saver = "+lambdaClassSaver);
            if (!path.isEmpty()) {
                throw new IllegalStateException();
            }
            return new VirtualPath(other);
        }

        @Override
        public String toString() {
            return path;
        }
    }

    private final class ClassChannel extends FakeSeekableByteChannel {
        private final Path path;
        private final ByteArrayOutputStream os;
        private final WritableByteChannel ch;

        public ClassChannel(Path path) {
            // System.err.println("[RRLL] ClassChannel created for path "+path
                    // +", saver = "+lambdaClassSaver+", this = "+this);
            this.path = path;
            this.os = new ByteArrayOutputStream();
            this.ch = Channels.newChannel(os);
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            return ch.write(src);
        }

        @Override
        public void close() {
            String className = path.toString();
            className = className.substring(0, className.lastIndexOf(".class"));
            // System.err.println("[RRLL] closing "+className+", use saver = "+lambdaClassSaver+" on CC "+this+" for path "+path);
            lambdaClassSaver.saveIfLambda(className, os.toByteArray());
        }
    }
}
