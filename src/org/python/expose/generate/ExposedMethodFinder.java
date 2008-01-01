package org.python.expose.generate;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.python.expose.MethodType;

/**
 * Visits a method passing all calls through to its delegate. If an ExposedNew or ExposedMethod
 * annotation is visited, calls handleResult with the exposer constructed with that annotation. Only
 * one of the handleResult methods will be called, if any.
 */
public abstract class ExposedMethodFinder extends MethodAdapter implements PyTypes, Opcodes {

    private Exposer newExp;

    private ExposedMethodVisitor methVisitor;

    private Type onType;

    private String methodDesc, typeName, methodName;

    private String[] exceptions;

    private int access;

    public ExposedMethodFinder(String typeName,
                               Type onType,
                               int access,
                               String name,
                               String desc,
                               String[] exceptions,
                               MethodVisitor delegate) {
        super(delegate);
        this.typeName = typeName;
        this.onType = onType;
        this.access = access;
        this.methodName = name;
        this.methodDesc = desc;
        this.exceptions = exceptions;
    }

    /**
     * @param exposer -
     *            the MethodExposer built as a result of visiting ExposeMethod
     */
    public abstract void handleResult(MethodExposer exposer);

    /**
     * @param exposer -
     *            the newExposer built as a result of visiting ExposeNew
     */
    public abstract void handleNewExposer(Exposer exposer);

    public abstract void exposeAsGetDescriptor(String descName);

    public abstract void exposeAsSetDescriptor(String descName);

    public abstract void exposeAsDeleteDescriptor(String descName);

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if(desc.equals(EXPOSED_NEW.getDescriptor())) {
            if((access & ACC_STATIC) != 0) {
                newExp = new NewExposer(onType, access, methodName, methodDesc, exceptions);
            } else {
                newExp = new OverridableNewExposer(onType,
                                                   Type.getType("L" + onType.getInternalName()
                                                           + "Derived;"),
                                                   access,
                                                   methodName,
                                                   methodDesc,
                                                   exceptions);
            }
        } else if(desc.equals(EXPOSED_METHOD.getDescriptor())) {
            methVisitor = new ExposedMethodVisitor();
            return methVisitor;
        } else if(desc.equals(EXPOSED_GET.getDescriptor())) {
            return new DescriptorVisitor(methodName) {

                @Override
                public void handleResult(String name) {
                    exposeAsGetDescriptor(name);
                }
            };
        } else if(desc.equals(EXPOSED_SET.getDescriptor())) {
            return new DescriptorVisitor(methodName) {

                @Override
                public void handleResult(String name) {
                    exposeAsSetDescriptor(name);
                }
            };
        } else if(desc.equals(EXPOSED_DELETE.getDescriptor())) {
            return new DescriptorVisitor(methodName) {

                @Override
                public void handleResult(String name) {
                    exposeAsDeleteDescriptor(name);
                }
            };
        }
        return super.visitAnnotation(desc, visible);
    }

    private abstract class StringArrayBuilder extends RestrictiveAnnotationVisitor {

        @Override
        public void visit(String name, Object value) {
            vals.add((String)value);
        }

        @Override
        public void visitEnd() {
            handleResult(vals.toArray(new String[vals.size()]));
        }

        public abstract void handleResult(String[] result);

        List<String> vals = new ArrayList<String>();
    }

    class ExposedMethodVisitor extends RestrictiveAnnotationVisitor {

        @Override
        public AnnotationVisitor visitArray(String name) {
            if(name.equals("names")) {
                return new StringArrayBuilder() {

                    @Override
                    public void handleResult(String[] result) {
                        names = result;
                    }
                };
            } else if(name.equals("defaults")) {
                return new StringArrayBuilder() {

                    @Override
                    public void handleResult(String[] result) {
                        defaults = result;
                    }
                };
            } else {
                return super.visitArray(name);
            }
        }

        @Override
        public void visitEnum(String name, String desc, String value) {
            if(name.equals("type")) {
                type = MethodType.valueOf(value);
            } else {
                super.visitEnum(name, desc, value);
            }
        }

        private String[] names = new String[0];

        private String[] defaults = new String[0];

        private MethodType type = MethodType.DEFAULT;
    }

    @Override
    public void visitEnd() {
        if(methVisitor != null) {
            handleResult(new MethodExposer(onType,
                                           access,
                                           methodName,
                                           methodDesc,
                                           typeName,
                                           methVisitor.names,
                                           methVisitor.defaults,
                                           methVisitor.type));
        }
        if(newExp != null) {
            handleNewExposer(newExp);
        }
        super.visitEnd();
    }
}
