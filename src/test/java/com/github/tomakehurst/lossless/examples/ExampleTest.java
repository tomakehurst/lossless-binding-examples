package com.github.tomakehurst.lossless.examples;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import javassist.*;
import javassist.bytecode.*;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.IntegerMemberValue;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

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

        CtClass contactDetailsClass = classPool.get(ImmutableContactDetails.class.getName());
        CtClass losslessContactDetailsClass = classPool.makeClass("com.github.tomakehurst.lossless.examples.LosslessImmutableContactDetails");


        losslessContactDetailsClass.setSuperclass(contactDetailsClass);

        CtField otherAttributesField = CtField.make(
                "java.util.Map other;",
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

        CtConstructor constructor = CtNewConstructor.make(
                "public LosslessImmutableContactDetails(String homePhone, String email) {\n" +
                "    super(homePhone, email);\n" +
                "}",
                losslessContactDetailsClass);
        addAnnotationWithNoParams(losslessContactDetailsClass, constructor, "com.fasterxml.jackson.annotation.JsonCreator");
        ParameterAnnotationsAttribute parameterAttributeInfo = getFirstConstructorParameterAnnotationsAttributeInfo(contactDetailsClass);
        constructor.getMethodInfo().addAttribute(parameterAttributeInfo);
        losslessContactDetailsClass.addConstructor(constructor);

        losslessContactDetailsClass.debugWriteFile("/Users/tomakehurst/dev/lossless-binding-examples/tmp");

        Class<? extends ImmutableContactDetails> modifiedContactDetailsClass = losslessContactDetailsClass.toClass();

        ImmutableContactDetails contactDetails = objectMapper.readValue(CONTACT_DETAILS_DOCUMENT, modifiedContactDetailsClass);
        String serialisedContactDetails = objectMapper.writeValueAsString(contactDetails);
        assertJsonEquals(CONTACT_DETAILS_DOCUMENT, serialisedContactDetails);
    }

    private void addAnnotationWithNoParams(CtClass ctClass, CtBehavior ctElement, String annotationClass) {
        ConstPool constpool = getConstPool(ctClass);
        AnnotationsAttribute annotationAttribute = new AnnotationsAttribute(constpool, AnnotationsAttribute.visibleTag);
        Annotation annotation = new Annotation(annotationClass, constpool);
        annotationAttribute.addAnnotation(annotation);
        ctElement.getMethodInfo().addAttribute(annotationAttribute);
    }

    private ConstPool getConstPool(CtClass ctClass) {
        ClassFile ccFile = ctClass.getClassFile();
        return ccFile.getConstPool();
    }

    private ParameterAnnotationsAttribute getFirstConstructorParameterAnnotationsAttributeInfo(CtClass ctClass) {
        CtConstructor ctConstructor = ctClass.getConstructors()[0];
        return (ParameterAnnotationsAttribute) ctConstructor.getMethodInfo().getAttribute(ParameterAnnotationsAttribute.visibleTag);
    }
}
