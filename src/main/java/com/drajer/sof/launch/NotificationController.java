package com.drajer.sof.launch;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.model.dstu2.composite.PeriodDt;
import ca.uhn.fhir.model.dstu2.resource.Bundle.Entry;
import ca.uhn.fhir.model.dstu2.resource.Encounter;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.drajer.eca.model.ActionRepo;
import com.drajer.ecrapp.util.ApplicationUtils;
import com.drajer.sof.model.ClientDetails;
import com.drajer.sof.model.LaunchDetails;
import com.drajer.sof.model.Notification;
import com.drajer.sof.model.R4FhirData;
import com.drajer.sof.service.ClientDetailsService;
import com.drajer.sof.service.LaunchService;
import com.drajer.sof.service.LoadingQueryR4Bundle;
import com.drajer.sof.utils.Authorization;
import com.drajer.sof.utils.FhirContextInitializer;
import com.drajer.sof.utils.RefreshTokenScheduler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.http.client.utils.URIBuilder;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.MessageHeader;
import org.hl7.fhir.r4.model.MessageHeader.MessageDestinationComponent;
import org.hl7.fhir.r4.model.MessageHeader.MessageSourceComponent;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class NotificationController {

  private final Logger logger = LoggerFactory.getLogger(NotificationController.class);

  private static final String FHIR_VERSION = "fhirVersion";
  private static final String ACCESS_TOKEN = "access_token";
  private static final String EXPIRES_IN = "expires_in";
  private static final String VALUE_URI = "valueUri";
  private static final String PATIENT = "patient";
  private static final String ENCOUNTER = "encounter";
  private static final String EXTENSION = "extension";

  @Autowired ClientDetailsService clientDetailsService;

  @Autowired RefreshTokenScheduler refreshTokenScheduler;

  @Autowired Authorization authorization;

  @Autowired LaunchService launchService;

  @Autowired LoadingQueryR4Bundle loadingQueryR4Bundle;

  @Autowired FhirContextInitializer fhirContextInitializer;

  @Autowired RestTemplate restTemplate;

  @CrossOrigin
  @RequestMapping(value = "/api/notify", method = RequestMethod.POST)
  public String invokeNotify(
      @RequestBody Notification notification,
      HttpServletRequest request,
      HttpServletResponse response)
      throws IOException {
    if (notification.getFhirServerURL() != null) {
      ClientDetails clientDetails =
          clientDetailsService.getClientDetailsByUrl(notification.getFhirServerURL());
      LaunchDetails launchDetails = new LaunchDetails();
      if (clientDetails != null) {
        String fhirVersion = "";
        String tokenEndpoint = "";
        JSONObject object =
            authorization.getMetadata(notification.getFhirServerURL() + "/metadata");

        if (object != null) {
          logger.info("Reading Metadata information");
          JSONObject security = (JSONObject) object.getJSONArray("rest").get(0);
          JSONObject sec = security.getJSONObject("security");
          JSONObject extension = (JSONObject) sec.getJSONArray(EXTENSION).get(0);
          JSONArray innerExtension = extension.getJSONArray(EXTENSION);
          if (object.getString(FHIR_VERSION).matches("1.(.*).(.*)")) {
            fhirVersion = FhirVersionEnum.DSTU2.toString();
            launchDetails.setFhirVersion(fhirVersion);
          }
          if (object.getString(FHIR_VERSION).matches("4.(.*).(.*)")) {
            fhirVersion = FhirVersionEnum.R4.toString();
            logger.info("Setting FHIR Version as :::::" + fhirVersion);
            launchDetails.setFhirVersion(fhirVersion);
          }
          for (int i = 0; i < innerExtension.length(); i++) {
            JSONObject urlExtension = innerExtension.getJSONObject(i);
            if (urlExtension.getString("url").equals("token")) {
              logger.info("Token URL::::: {}", urlExtension.getString(VALUE_URI));
              tokenEndpoint = urlExtension.getString(VALUE_URI);
              clientDetails.setTokenURL(tokenEndpoint);
            }
          }
          JSONObject accessTokenObj = refreshTokenScheduler.getBackendAppAccessToken(clientDetails);
          if (accessTokenObj != null) {

            launchDetails.setAccessToken(accessTokenObj.getString(ACCESS_TOKEN));
            launchDetails.setFhirVersion(fhirVersion);
            launchDetails.setAssigningAuthorityId(clientDetails.getAssigningAuthorityId());
            launchDetails.setClientId(clientDetails.getClientId());
            launchDetails.setClientSecret(clientDetails.getClientSecret());
            launchDetails.setScope(clientDetails.getScopes());
            launchDetails.setDirectHost(clientDetails.getDirectHost());
            launchDetails.setDirectPwd(clientDetails.getDirectPwd());
            launchDetails.setSmtpPort(clientDetails.getSmtpPort());
            launchDetails.setImapPort(clientDetails.getImapPort());
            launchDetails.setDirectRecipient(clientDetails.getDirectRecipientAddress());
            launchDetails.setDirectUser(clientDetails.getDirectUser());
            launchDetails.setEhrServerURL(clientDetails.getFhirServerBaseURL());
            launchDetails.setEncounterId(notification.getEncounterId());
            launchDetails.setExpiry(accessTokenObj.getInt("expires_in"));
            launchDetails.setFhirVersion(fhirVersion);
            launchDetails.setIsCovid(clientDetails.getIsCovid());
            launchDetails.setLaunchPatientId(notification.getPatientId());
            launchDetails.setTokenUrl(clientDetails.getTokenURL());
            launchDetails.setVersionNumber(1);
            launchDetails.setDebugFhirQueryAndEicr(clientDetails.getDebugFhirQueryAndEicr());
            launchDetails.setRequireAud(clientDetails.getRequireAud());
            launchDetails.setRestAPIURL(clientDetails.getRestAPIURL());

            setStartAndEndDates(clientDetails, launchDetails);

            Bundle loadingQueryBundle =
                loadingQueryR4Bundle.createR4Bundle(
                    launchDetails,
                    new R4FhirData(),
                    launchDetails.getStartDate(),
                    launchDetails.getEndDate());

            Bundle responseBundle = generateBundleResponse(loadingQueryBundle, clientDetails);

            logger.info(
                "Generated Response Bundle:::::"
                    + FhirContext.forR4().newJsonParser().encodeResourceToString(responseBundle));
            String bundle =
                FhirContext.forR4().newJsonParser().encodeResourceToString(responseBundle);

            String fileName =
                ActionRepo.getInstance().getLogFileDirectory()
                    + "/ReportingBundle-"
                    + launchDetails.getLaunchPatientId()
                    + ".json";
            ApplicationUtils.saveDataToFile(bundle, fileName);
            submitResponseBundleToEndpoint(clientDetails, bundle);
          } else {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Failed to get Authorization");
          }
        }
      } else {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unrecognized client");
      }
    } else {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Please provide FHIR Server URL and Patient Id");
    }

    return "Notified successfully";
  }

  private void submitResponseBundleToEndpoint(ClientDetails clientDetails, String responseBundle) {
    // TODO Auto-generated method stub
    URIBuilder ub = null;
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
      JSONObject json = new JSONObject(responseBundle);
      final HttpEntity<JSONObject> request = new HttpEntity<>(json, headers);

      logger.info(clientDetails.getRestAPIURL());

      ub = new URIBuilder(clientDetails.getRestAPIURL());

      ResponseEntity<String> response =
          restTemplate.exchange(ub.toString(), HttpMethod.POST, request, String.class);
    } catch (Exception e) {
      logger.error("Error in Submitting the bundle to PHA Endpoint");
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Error in submitting the Bundle to PHA Endpoint", e);
    }
  }

  private Bundle generateBundleResponse(Bundle loadingQueryBundle, ClientDetails clientDetails) {
    // Set Id to LoadingQuery Bundle
    loadingQueryBundle.setId(UUID.randomUUID().toString());

    Bundle bundle = new Bundle();

    Organization senderOrganization = null;
    Resource resource = getResourceFromBundle(loadingQueryBundle, Organization.class);
    if (resource != null) {
      senderOrganization = (Organization) resource;
    }

    MessageHeader messageHeader =
        constructMessageHeaderResource(clientDetails, senderOrganization, loadingQueryBundle);

    // Set Id for Response Bundle
    bundle.setId(UUID.randomUUID().toString());

    // Set Meta for Response Bundle
    Meta meta = new Meta();
    meta.setVersionId("1");
    meta.setLastUpdated(new Date());
    List<CanonicalType> profilesList = new ArrayList<CanonicalType>();
    CanonicalType profile = new CanonicalType();
    profile.setValue("http://hl7.org/fhir/us/medmorph/StructureDefinition/us-ph-reporting-bundle");
    profilesList.add(profile);
    meta.setProfile(profilesList);
    bundle.setMeta(meta);

    // Set Type for Response Bundle
    bundle.setType(BundleType.MESSAGE);
    bundle.setTimestamp(new Date());
    List<BundleEntryComponent> entryList = new ArrayList<Bundle.BundleEntryComponent>();

    BundleEntryComponent messageHeaderEntryComponent = new BundleEntryComponent();
    messageHeaderEntryComponent.setResource(messageHeader);
    messageHeaderEntryComponent.setFullUrl(
        "MessageHeader/" + messageHeader.getIdElement().getIdPart());
    entryList.add(messageHeaderEntryComponent);

    BundleEntryComponent bundleEntryComponent = new BundleEntryComponent();
    bundleEntryComponent.setResource(loadingQueryBundle);
    bundleEntryComponent.setFullUrl("Bundle/" + loadingQueryBundle.getIdElement().getIdPart());
    entryList.add(bundleEntryComponent);

    bundle.setEntry(entryList);

    return bundle;
  }

  private MessageHeader constructMessageHeaderResource(
      ClientDetails clientDetails, Organization organization, Bundle loadingQueryBundle) {
    // TODO Auto-generated method stub
    MessageHeader messageHeader = new MessageHeader();

    // Set Id
    messageHeader.setId(UUID.randomUUID().toString());

    // Set Meta
    Meta meta = new Meta();
    meta.setVersionId("1");
    meta.setLastUpdated(new Date());
    List<CanonicalType> profilesList = new ArrayList<CanonicalType>();
    CanonicalType profile = new CanonicalType();
    profile.setValue("http://hl7.org/fhir/us/medmorph/StructureDefinition/us-ph-messageheader");
    profilesList.add(profile);
    meta.setProfile(profilesList);
    messageHeader.setMeta(meta);

    // Set Extensions
    List<Extension> extensionsList = new ArrayList<Extension>();

    Extension dataEncryptedExtension = new Extension();
    dataEncryptedExtension.setUrl(
        "http://hl7.org/fhir/us/medmorph/StructureDefinition/ext-dataEncrypted");
    dataEncryptedExtension.setValue(new BooleanType(false));
    extensionsList.add(dataEncryptedExtension);

    Extension categoryExtension = new Extension();
    categoryExtension.setUrl(
        "http://hl7.org/fhir/us/medmorph/StructureDefinition/ext-messageProcessingCategory");
    categoryExtension.setValue(new StringType("consequence"));
    extensionsList.add(categoryExtension);

    messageHeader.setExtension(extensionsList);

    // Set Event Coding
    Coding eventCoding = new Coding();
    eventCoding.setSystem(
        "http://hl7.org/fhir/us/medmorph/CodeSystem/us-ph-messageheader-message-types");
    eventCoding.setCode("birth-death-reporting");
    messageHeader.setEvent(eventCoding);

    // Set Destination
    List<MessageDestinationComponent> messageDestinationComponents =
        new ArrayList<MessageHeader.MessageDestinationComponent>();
    MessageDestinationComponent messageDestinationComponent = new MessageDestinationComponent();
    messageDestinationComponent.setName("PHA endpoint");
    messageDestinationComponent.setEndpoint(clientDetails.getRestAPIURL());
    messageDestinationComponents.add(messageDestinationComponent);
    messageHeader.setDestination(messageDestinationComponents);

    // Set Sender Organization
    if (organization != null) {
      Reference reference = new Reference();
      reference.setReference("Organization/" + organization.getIdElement().getIdPart());
      messageHeader.setSender(reference);
    }

    // Set Source
    MessageSourceComponent messageSourceComponent = new MessageSourceComponent();
    messageSourceComponent.setName("Healthcare Organization");
    messageSourceComponent.setSoftware("Backend Service App");
    messageSourceComponent.setVersion("3.1.45.AABB");
    ContactPoint contactPoint = new ContactPoint();
    contactPoint.setSystem(ContactPoint.ContactPointSystem.fromCode("phone"));
    contactPoint.setValue("+1 (917) 123 4567");
    messageSourceComponent.setContact(contactPoint);
    messageSourceComponent.setEndpoint("http://example.healthcare.org/fhir");
    messageHeader.setSource(messageSourceComponent);

    // Set Reason
    CodeableConcept messageHeaderReason = new CodeableConcept();
    List<Coding> messageHeaderReasonCodings = new ArrayList<Coding>();
    Coding messageCoding = new Coding();
    messageCoding.setCode("birth-death-reporting");
    messageCoding.setSystem(
        "http://hl7.org/fhir/us/medmorph/CodeSystem/us-ph-triggerdefinition-namedevents");
    messageHeaderReasonCodings.add(messageCoding);
    messageHeaderReason.setCoding(messageHeaderReasonCodings);
    messageHeader.setReason(messageHeaderReason);

    // Set Focus
    List<Reference> referenceList = new ArrayList<Reference>();
    Reference focusReference = new Reference();
    focusReference.setReference("Bundle/" + loadingQueryBundle.getIdElement().getIdPart());
    referenceList.add(focusReference);
    messageHeader.setFocus(referenceList);

    return messageHeader;
  }

  public Resource getResourceFromBundle(Bundle bundle, Class<?> resource) {
    try {
      for (BundleEntryComponent entry : bundle.getEntry()) {
        if (entry.getResource() != null && entry.getResource().getClass() == resource) {
          return entry.getResource();
        }
      }
    } catch (Exception e) {
      logger.error("Error in getting the Resource from Bundle", e);
    }
    return null;
  }

  public void setStartAndEndDates(ClientDetails clientDetails, LaunchDetails currentStateDetails) {
    logger.info(currentStateDetails.getFhirVersion());
    logger.info(currentStateDetails.getClientId());
    FhirContext context =
        fhirContextInitializer.getFhirContext(currentStateDetails.getFhirVersion());

    IGenericClient client =
        fhirContextInitializer.createClient(
            context, currentStateDetails.getEhrServerURL(), currentStateDetails.getAccessToken());
    PeriodDt dstu2Period = null;
    Period r4Period = null;

    if (currentStateDetails.getFhirVersion().equals(FhirVersionEnum.DSTU2.toString())
        && currentStateDetails.getEncounterId() != null) {
      logger.info("DSTU2");
      Encounter encounter =
          (Encounter)
              fhirContextInitializer.getResouceById(
                  currentStateDetails,
                  client,
                  context,
                  "Encounter",
                  currentStateDetails.getEncounterId());
      if (encounter.getPeriod() != null) {
        dstu2Period = encounter.getPeriod();
      }
    } else if (currentStateDetails.getFhirVersion().equals(FhirVersionEnum.DSTU2.toString())) {
      Encounter encounter = new Encounter();
      // If Encounter Id is not Present in Launch Details Get Encounters by Patient Id
      // and Find the latest Encounter
      ca.uhn.fhir.model.dstu2.resource.Bundle bundle =
          (ca.uhn.fhir.model.dstu2.resource.Bundle)
              fhirContextInitializer.getResourceByPatientId(
                  currentStateDetails, client, context, "Encounter");
      if (!bundle.getEntry().isEmpty()) {
        Map<Encounter, Date> encounterMap = new HashMap<>();
        for (Entry entry : bundle.getEntry()) {
          Encounter encounterEntry = (Encounter) entry.getResource();
          String encounterId = encounterEntry.getIdElement().getIdPart();
          logger.info("Received Encounter Id========> {}", encounterId);
          encounterMap.put(encounterEntry, encounterEntry.getMeta().getLastUpdated());
        }
        encounter = Collections.max(encounterMap.entrySet(), Map.Entry.comparingByValue()).getKey();
        if (encounter != null) {
          currentStateDetails.setEncounterId(encounter.getIdElement().getIdPart());
          if (encounter.getPeriod() != null) {
            dstu2Period = encounter.getPeriod();
          }
        }
      }
    }

    if (currentStateDetails.getFhirVersion().equals(FhirVersionEnum.R4.toString())
        && currentStateDetails.getEncounterId() != null) {
      org.hl7.fhir.r4.model.Encounter r4Encounter =
          (org.hl7.fhir.r4.model.Encounter)
              fhirContextInitializer.getResouceById(
                  currentStateDetails,
                  client,
                  context,
                  "Encounter",
                  currentStateDetails.getEncounterId());
      if (r4Encounter != null && r4Encounter.getPeriod() != null) {
        r4Period = r4Encounter.getPeriod();
      }
    } else if (currentStateDetails.getFhirVersion().equals(FhirVersionEnum.R4.toString())) {
      org.hl7.fhir.r4.model.Encounter r4Encounter;
      // If Encounter Id is not Present in Launch Details Get Encounters by Patient Id
      // and Find the latest Encounter
      Bundle bundle =
          (Bundle)
              fhirContextInitializer.getResourceByPatientId(
                  currentStateDetails, client, context, "Encounter");

      if (!bundle.getEntry().isEmpty()) {
        Map<org.hl7.fhir.r4.model.Encounter, Date> encounterMap = new HashMap<>();
        for (BundleEntryComponent entry : bundle.getEntry()) {
          org.hl7.fhir.r4.model.Encounter encounterEntry =
              (org.hl7.fhir.r4.model.Encounter) entry.getResource();
          String encounterId = encounterEntry.getIdElement().getIdPart();
          logger.info("Received Encounter Id========> {}", encounterId);
          if (encounterEntry.hasMeta()) {
            encounterMap.put(encounterEntry, encounterEntry.getMeta().getLastUpdated());
          }
        }
        logger.info("Encounters added to MAP:::::" + encounterMap.size());
        if (encounterMap.size() > 0) {
          r4Encounter =
              Collections.max(encounterMap.entrySet(), Map.Entry.comparingByValue()).getKey();
          if (r4Encounter != null) {
            currentStateDetails.setEncounterId(r4Encounter.getIdElement().getIdPart());
            if (r4Encounter.getPeriod() != null) {
              r4Period = r4Encounter.getPeriod();
            }
          }
        }
      }
    }

    if (dstu2Period != null) {
      if (dstu2Period.getStart() != null) {
        currentStateDetails.setStartDate(dstu2Period.getStart());
      } else {
        currentStateDetails.setStartDate(getDate(clientDetails.getEncounterStartThreshold()));
      }
      if (dstu2Period.getEnd() != null) {
        currentStateDetails.setEndDate(dstu2Period.getEnd());
      } else {
        currentStateDetails.setEndDate(getDate(clientDetails.getEncounterEndThreshold()));
      }
    } else if (r4Period != null) {
      if (r4Period.getStart() != null) {
        currentStateDetails.setStartDate(r4Period.getStart());
      } else {
        currentStateDetails.setStartDate(getDate(clientDetails.getEncounterStartThreshold()));
      }
      if (r4Period.getEnd() != null) {
        currentStateDetails.setEndDate(r4Period.getEnd());
      } else {
        currentStateDetails.setEndDate(getDate(clientDetails.getEncounterEndThreshold()));
      }
    } else {
      currentStateDetails.setStartDate(null);
      currentStateDetails.setEndDate(null);
    }
  }

  private static Date getDate(String thresholdValue) {
    return DateUtils.addHours(new Date(), Integer.parseInt(thresholdValue));
  }
}
