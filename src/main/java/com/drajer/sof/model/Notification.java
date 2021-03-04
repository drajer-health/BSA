package com.drajer.sof.model;

public class Notification {

  private String fhirServerURL;
  private String patientId;
  private String encounterId;
  private String eventType;
  private String rawMessage;

  public String getFhirServerURL() {
    return fhirServerURL;
  }

  public void setFhirServerURL(String fhirServerURL) {
    this.fhirServerURL = fhirServerURL;
  }

  public String getPatientId() {
    return patientId;
  }

  public void setPatientId(String patientId) {
    this.patientId = patientId;
  }

  public String getEncounterId() {
    return encounterId;
  }

  public void setEncounterId(String encounterId) {
    this.encounterId = encounterId;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getRawMessage() {
    return rawMessage;
  }

  public void setRawMessage(String rawMessage) {
    this.rawMessage = rawMessage;
  }
}
