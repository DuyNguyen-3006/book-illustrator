package com.bookillustrator.interfaces.rest.request;

/** style is optional and only meaningful for the STYLE step — spec §4.4. */
public record RunStepRequest(String style) {
}
