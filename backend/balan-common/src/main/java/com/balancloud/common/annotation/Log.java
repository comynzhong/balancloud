package com.balancloud.common.annotation;

import com.balancloud.common.annotation.BusinessType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Log {

    String title() default "";

    BusinessType businessType() default BusinessType.OTHER;

    boolean saveRequestData() default true;

    boolean saveResponseData() default true;
}
