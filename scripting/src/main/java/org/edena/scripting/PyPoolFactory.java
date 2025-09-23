package org.edena.scripting;

import com.google.inject.BindingAnnotation;
import java.lang.annotation.*;

@BindingAnnotation
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PyPoolFactory {}
