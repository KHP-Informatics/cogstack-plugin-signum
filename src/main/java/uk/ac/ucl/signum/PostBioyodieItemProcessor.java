package uk.ac.ucl.signum;

import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import uk.ac.kcl.model.Document;

import java.lang.ClassCastException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import javax.annotation.PostConstruct;

@Profile("postbioyodie")
@Service("PostBioyodieItemProcessor")
public class PostBioyodieItemProcessor implements ItemProcessor<Document, Document> {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(PostBioyodieItemProcessor.class);

    @Autowired
    Environment env;

    @Value("${webservice.fieldName}")
    private String bioYodieFieldName;

    @PostConstruct
    public void init() {

    }


    @Override
    public Document process(final Document doc) throws Exception {
      LOG.info("Starting {} on doc {}", this.getClass().getSimpleName(), doc.getDocName());

      long startTime = System.currentTimeMillis();
      try {

        if (bioYodieFieldName != null) {
          // Map<String, Object> mentionList;
          Object bioyodieMapObj;
          Object entitiesMapObj;
          Object mentionListObj;
          String bioyodieText;
          bioyodieMapObj = doc.getAssociativeArray().getOrDefault(bioYodieFieldName, null);
          if (bioyodieMapObj != null) {
            entitiesMapObj = ((Map<String, Object>) bioyodieMapObj).getOrDefault("entities", null);
            bioyodieText = (String) ((Map<String, Object>) bioyodieMapObj).getOrDefault("text", "");
            if (entitiesMapObj != null) {
              mentionListObj = ((Map<String, Object>) entitiesMapObj).getOrDefault("Mention", null);
              if (mentionListObj != null) {
                //doc.getAssociativeArray().put(bioYodieFieldName, mentionListObj);
                sortMentionList(mentionListObj);

                //doc.getAssociativeArray().put("mentioned_"+bioYodieFieldName, sortMentionList(mentionListObj));
                //doc.getAssociativeArray().put("affirmed_"+bioYodieFieldName+"_name", getAffirmed(mentionListObj));
                //doc.getAssociativeArray().put("negated_"+bioYodieFieldName+"_name", getNegated(mentionListObj));
                //doc.getAssociativeArray().put(bioYodieFieldName+"_raw_text", getOriginal(bioyodieText, mentionListObj));

                doc.getAssociativeArray().put(bioYodieFieldName+"_cid", getConceptAttr(mentionListObj, "inst", -1));

                if (isAffirmedSmoker(mentionListObj)) {
                  doc.getAssociativeArray().put("X-BIO-YODIE-IS-AFFIRMED-SMOKER", "yes");
                } else {
                  doc.getAssociativeArray().put("X-BIO-YODIE-IS-AFFIRMED-SMOKER", "unknown");
                }
                for (Map.Entry<String, List<String>> entry : getClassifiedList(bioyodieText, mentionListObj).entrySet()) {
                  doc.getAssociativeArray().put(entry.getKey(), entry.getValue());
                }

                doc.getAssociativeArray().remove(bioYodieFieldName);
                doc.getAssociativeArray().put("X-PLUGINS-POST-BIO-YODIE", "success");
              }
            }
          }
        }
        long endTime = System.currentTimeMillis();
        LOG.info("{};Time:{} ms",
                 this.getClass().getSimpleName(),
                 endTime - startTime);
        LOG.info("Finished {} on doc {}", this.getClass().getSimpleName(), doc.getDocName());
      } catch (ClassCastException castEx) {
        LOG.warn("ClassCastException caught, possibly due to malformed result", castEx);
        doc.getAssociativeArray().put("X-PLUGINS-POST-BIO-YODIE", "failed");
      } catch (Exception e) {
        LOG.error("Exception caught", e);
        doc.getAssociativeArray().put("X-PLUGINS-POST-BIO-YODIE", "failed");
      }
      finally {
        return doc;
      }
    }

    private List<? extends Object> sortMentionList(Object list) {
       List<Map<String, Object>> mentionList = (List<Map<String, Object>>) list;
       // Arrays.sort(mentionList, new MentionComparator());
       mentionList.sort(new MentionComparator());
       return mentionList;
     }

     private List<? extends Object> getOriginal(String text, Object list) {
       List<Map<String, Object>> mentionList = (List<Map<String, Object>>) list;
       List<String> res = new ArrayList<String>();

       for (Map<String, Object> map: mentionList) {

         int start = ((ArrayList<Number>) map.get("indices")).get(0).intValue();
         int end = ((ArrayList<Number>) map.get("indices")).get(1).intValue();

         if (start < end && end < text.length()) {
           res.add(text.substring(start, end));

           map.put("x-original-text", text.substring(start, end));
         }
       }
       return res;
     }

     private List<? extends Object> getConceptAttr(Object list, String key, int negationFlag) {
       List<Map<String, Object>> mentionList = (List<Map<String, Object>>) list;
       List<String> res = new ArrayList<String>();

       for (Map<String, Object> map: mentionList) {
         String term = (String) map.getOrDefault(key, "");
         String negation = (String) map.getOrDefault("Negation", "");
         if (negation.equalsIgnoreCase("Affirmed") && negationFlag == 0) {
           res.add(term);
         } else if (negation.equalsIgnoreCase("Negated") && negationFlag == 1) {
           res.add(term);
         } else if (negationFlag == -1) {
           res.add(term);
         }
       }
       return res;
     }

     private Map<String, List<String>> getClassifiedList(String text, Object list) {
       List<Map<String, Object>> mentionList = (List<Map<String, Object>>) list;
       Map<String, List<String>> res = new HashMap<String, List<String>>();

       Set<String> seenSty = new HashSet<String>();

       // Catch all lists:
       String listNameAllAffirmedConcept = "Affirmed_Mention";
       String listNameAllAffirmedRawText = "Affirmed_Mention_Raw";
       String listNameAllNegatedConcept = "Negated_Mention";
       String listNameAllNegatedRawText = "Negated_Mention_Raw";

       res.put(listNameAllAffirmedConcept, new ArrayList<String>());
       res.put(listNameAllAffirmedRawText, new ArrayList<String>());
       res.put(listNameAllNegatedConcept, new ArrayList<String>());
       res.put(listNameAllNegatedRawText, new ArrayList<String>());

       for (Map<String, Object> map: mentionList) {

         int start = ((ArrayList<Number>) map.get("indices")).get(0).intValue();
         int end = ((ArrayList<Number>) map.get("indices")).get(1).intValue();
         String sty = (String) map.get("STY");
         sty = sty.replace(",", "").replace(" ", "_");

         String listNameAffirmedConcept = "Affirmed_" + sty;
         String listNameAffirmedRawText = "Affirmed_" + sty + "_Raw";
         String listNameNegatedConcept = "Negated_" + sty;
         String listNameNegatedRawText = "Negated_" + sty + "_Raw";

         if (!seenSty.contains(sty)) {
           seenSty.add(sty);
           res.put(listNameAffirmedConcept, new ArrayList<String>());
           res.put(listNameAffirmedRawText, new ArrayList<String>());
           res.put(listNameNegatedConcept, new ArrayList<String>());
           res.put(listNameNegatedRawText, new ArrayList<String>());
         }

         String rawText = "";
         if (start < end && end < text.length()) {
           rawText = text.substring(start, end);
         }

         String negation = (String) map.getOrDefault("Negation", "");
         String term = (String) map.getOrDefault("PREF", "");
         if (negation.equalsIgnoreCase("Affirmed")) {
           // Affirmed
           res.get(listNameAffirmedConcept).add(term);
           res.get(listNameAffirmedRawText).add(rawText);

           res.get(listNameAllAffirmedConcept).add(term);
           res.get(listNameAllAffirmedRawText).add(rawText);


         } else if (negation.equalsIgnoreCase("Negated")) {
           // Negated
           res.get(listNameNegatedConcept).add(term);
           res.get(listNameNegatedRawText).add(rawText);

           res.get(listNameAllNegatedConcept).add(term);
           res.get(listNameAllNegatedRawText).add(rawText);
         }
       }
       return res;
     }

     private List<? extends Object> getAffirmed(Object list) {
       List<Map<String, Object>> mentionList = (List<Map<String, Object>>) list;
       List<String> affirmed = new ArrayList<String>();

       for (Map<String, Object> map: mentionList) {
         String term = (String) map.getOrDefault("PREF", "");
         String negation = (String) map.getOrDefault("Negation", "");
         if (negation.equalsIgnoreCase("Affirmed")) {
           affirmed.add(term);
         }
       }
       return affirmed;
     }

     private List<? extends Object> getNegated(Object list) {
       List<Map<String, Object>> mentionList = (List<Map<String, Object>>) list;
       List<String> negated = new ArrayList<String>();

       for (Map<String, Object> map: mentionList) {
         String term = (String) map.getOrDefault("PREF", "");
         String negation = (String) map.getOrDefault("Negation", "");
         if (negation.equalsIgnoreCase("Negated")) {
           negated.add(term);
         }
       }
       return negated;
     }

     private boolean isAffirmedSmoker(Object list) {
       List<Map<String, Object>> mentionList = (List<Map<String, Object>>) list;

       for (Map<String, Object> map: mentionList) {
         String term = (String) map.getOrDefault("inst", "");
         String negation = (String) map.getOrDefault("Negation", "");
         if (term.equals("C3241966") && negation.equalsIgnoreCase("Affirmed")) {
           return true;
         }
       }

       return false;
     }

 class MentionComparator implements Comparator<Map<String, Object>> {

   @Override
   public int compare(Map<String, Object> o1, Map<String, Object> o2) {
     int start1 = ((List<Number>) o1.get("indices")).get(0).intValue();
     int start2 = ((List<Number>) o2.get("indices")).get(0).intValue();
     return start1 - start2;
   }
 }
}
