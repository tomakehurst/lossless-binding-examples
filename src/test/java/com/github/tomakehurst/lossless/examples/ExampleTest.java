package com.github.tomakehurst.lossless.examples;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import javassist.*;
import javassist.bytecode.*;
import javassist.bytecode.annotation.Annotation;
import org.junit.Before;
import org.junit.Test;

import static net.sf.json.test.JSONAssert.assertJsonEquals;

public class ExampleTest {

    private ObjectMapper objectMapper;

    @Before
    public void init() {
        objectMapper = new ObjectMapper();
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    static final String CONTACT_DETAILS_DOCUMENT =
            "{                                  \n" +
            " \"homePhone\": \"01234 567890\",  \n" +
            " \"mobilePhone\": \"07123 123456\",\n" +
            " \"email\": \"someone@email.com\", \n" +
            " \"address\": {                    \n" +
            "  \"line1\": \"1 Toad Road\",      \n" +
            "  \"city\": \"London\",            \n" +
            "  \"postcode\": \"E1 1TD\"         \n" +
            " }                                 \n" +
            "}";

    @Test
    public void additionalFieldsAreLostByDefault() throws Exception {
        ContactDetails contactDetails = objectMapper.readValue(CONTACT_DETAILS_DOCUMENT, ContactDetails.class);

        // This will fail
        String serialisedContactDetails = objectMapper.writeValueAsString(contactDetails);
        assertJsonEquals(CONTACT_DETAILS_DOCUMENT, serialisedContactDetails);
    }

    @Test
    public void additionalFieldsCanBePreservedInMutableMapProperty() throws Exception {
        MutableLosslessContactDetails contactDetails = objectMapper.readValue(CONTACT_DETAILS_DOCUMENT, MutableLosslessContactDetails.class);

        // This should succeed
        String serialisedContactDetails = objectMapper.writeValueAsString(contactDetails);
        assertJsonEquals(CONTACT_DETAILS_DOCUMENT, serialisedContactDetails);
    }

    @Test
    public void fieldPreservationCanBeAddedToImmutableClassViaBytecodeManipulation() throws Exception {
        ClassPool classPool = ClassPool.getDefault();

        CtClass losslessContactDetailsClass = classPool.get(ImmutableContactDetails.class.getName());
        losslessContactDetailsClass.setName("com.github.tomakehurst.lossless.examples.LosslessImmutableContactDetails");
        losslessContactDetailsClass.setSuperclass(classPool.get(ImmutableContactDetails.class.getName()));
        removeAllFieldsAndMethods(losslessContactDetailsClass);
        losslessContactDetailsClass.getConstructors()[0].setBody("super($1, $2);");

        CtField otherAttributesField = CtField.make(
                "java.util.Map other = new java.util.LinkedHashMap();",
                losslessContactDetailsClass);
        losslessContactDetailsClass.addField(otherAttributesField);

        CtMethod anyMethod = CtNewMethod.make(
                "public java.util.Map any() {\n" +
                "    return other;\n" +
                "}",
                losslessContactDetailsClass);
        addAnnotationWithNoParams(losslessContactDetailsClass, anyMethod, "com.fasterxml.jackson.annotation.JsonAnyGetter");
        losslessContactDetailsClass.addMethod(anyMethod);

        CtMethod setMethod = CtNewMethod.make(
                "public void set(String name, Object value) {\n" +
                "    other.put(name, value);\n" +
                "}",
                losslessContactDetailsClass);
        addAnnotationWithNoParams(losslessContactDetailsClass, setMethod, "com.fasterxml.jackson.annotation.JsonAnySetter");
        losslessContactDetailsClass.addMethod(setMethod);

        losslessContactDetailsClass.debugWriteFile("/Users/tomakehurst/dev/lossless-binding-examples/tmp");

        Class<? extends ImmutableContactDetails> modifiedContactDetailsClass = losslessContactDetailsClass.toClass();

        ImmutableContactDetails contactDetails = objectMapper.readValue(CONTACT_DETAILS_DOCUMENT, modifiedContactDetailsClass);
        String serialisedContactDetails = objectMapper.writeValueAsString(contactDetails);
        System.out.println(serialisedContactDetails);
        assertJsonEquals(CONTACT_DETAILS_DOCUMENT, serialisedContactDetails);
    }

    private void removeAllFieldsAndMethods(CtClass losslessContactDetailsClass) throws NotFoundException {
        for (CtField field: losslessContactDetailsClass.getDeclaredFields()) {
            losslessContactDetailsClass.removeField(field);
        }

        for (CtMethod method: losslessContactDetailsClass.getDeclaredMethods()) {
            losslessContactDetailsClass.removeMethod(method);
        }
    }

    private void addAnnotationWithNoParams(CtClass ctClass, CtBehavior ctElement, String annotationClass) {
        ConstPool constpool = getConstPool(ctClass);
        AnnotationsAttribute annotationAttribute = new AnnotationsAttribute(constpool, AnnotationsAttribute.visibleTag);
        Annotation annotation = new Annotation(annotationClass, constpool);
        annotationAttribute.addAnnotation(annotation);
        ctElement.getMethodInfo().addAttribute(annotationAttribute);
    }

    private void addConstructorParameterAnnotation(CtConstructor ctConstructor, String annotationClass, String value) {

    }

    private ConstPool getConstPool(CtClass ctClass) {
        ClassFile ccFile = ctClass.getClassFile();
        return ccFile.getConstPool();
    }

    private ParameterAnnotationsAttribute cloneFirstConstructorParameterAnnotationsAttributeInfo(CtClass ctClass) {
        CtConstructor ctConstructor = ctClass.getConstructors()[0];
        ParameterAnnotationsAttribute originalAnnotationsAttribute = (ParameterAnnotationsAttribute) ctConstructor.getMethodInfo().getAttribute(ParameterAnnotationsAttribute.visibleTag);
        Annotation[][] sourceAnnotations = originalAnnotationsAttribute.getAnnotations();

        ParameterAnnotationsAttribute targetAnnotationsAttribute = new ParameterAnnotationsAttribute(getConstPool(ctClass), ParameterAnnotationsAttribute.visibleTag);
        Annotation[][] targetAnnotations = new Annotation[sourceAnnotations.length][sourceAnnotations[0].length];
        for (int i = 0; i < sourceAnnotations.length; i++) {
            for (int j = 0; j < sourceAnnotations[0].length; j++) {
                targetAnnotations[i][j] = sourceAnnotations[i][j];
            }
        }

        targetAnnotationsAttribute.setAnnotations(targetAnnotations);
        return targetAnnotationsAttribute;

    }
}
