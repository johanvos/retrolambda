// Copyright © 2013-2014 Esko Luontola <www.orfjackal.net>
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

package net.orfjackal.retrolambda;

import org.objectweb.asm.*;

import static org.objectweb.asm.Opcodes.*;

public class RemoveDefaultMethodBodies extends ClassVisitor {

    private boolean isInterface;

    public RemoveDefaultMethodBodies(ClassVisitor next) {
        super(ASM5, next);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        isInterface = Flags.hasFlag(access, ACC_INTERFACE);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if (isInterface && isDefaultMethod(access)) {
            MethodVisitor next = super.visitMethod(access | ACC_ABSTRACT, name, desc, signature, exceptions);
            // TODO: annotations
            return null;
        } else if (isInterface && isStaticMethod(access)) {
            return null;
        } else {
            return super.visitMethod(access, name, desc, signature, exceptions);
        }
    }

    private static boolean isDefaultMethod(int access) {
        return !Flags.hasFlag(access, ACC_ABSTRACT)
                && !Flags.hasFlag(access, ACC_STATIC);
    }

    private static boolean isStaticMethod(int access) {
        return Flags.hasFlag(access, ACC_STATIC);
    }
}