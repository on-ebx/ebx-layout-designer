import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.on.ebx.layout.runtime.*;
import java.util.*;
/** Checks of Audience (who sees what) and Personal (a user's own arrangement): tools/test.sh. */
public class AudienceTest {
  static int fail = 0;
  static void check(String what, Object got, Object want){ if (!String.valueOf(got).equals(String.valueOf(want))){ fail++; System.out.println("FAIL " + what + ": got " + got + " want " + want);} else System.out.println("ok   " + what); }
  static String tabs(JsonNode l){ List<String> o = new ArrayList<>(); for (JsonNode t : l.path("tabs")) o.add(t.path("id").asText()); return String.join(",", o); }
  static String blocks(JsonNode l){ List<String> o = new ArrayList<>(); for (JsonNode t : l.path("tabs")) for (JsonNode r : t.path("rows")) for (JsonNode c : r.path("blocks")) for (JsonNode b : c) o.add(b.path("id").asText() + (b.path("ro").asBoolean() ? "(ro)" : "") + (b.path("_collapsed").asBoolean() ? "(fold)" : "")); return String.join(",", o); }
  public static void main(String[] a) throws Exception {
    ObjectMapper m = new ObjectMapper();
    JsonNode L = m.readTree("""
    {"format":"ebx-form-layout/1","personal":true,
     "tabs":[
      {"id":"ov","label":{"fr-FR":"Vue"},"rows":[{"columns":2,"blocks":[[{"id":"id1","type":"panel","roRoles":["Acheteur"],"fields":[{"p":"/a"},{"p":"/b","roles":["Steward"]},{"p":"/c","roRoles":["Acheteur"]}]},{"id":"pay","type":"panel","roles":["Acheteur","Compta"],"locked":true,"fields":[{"p":"/x"}]}],[{"id":"src","type":"sourceValues","roles":["Steward"]}]]}]},
      {"id":"com","label":{"fr-FR":"Commercial"},"roles":["Acheteur","Compta"],"rows":[{"columns":1,"blocks":[[{"id":"buy","type":"panel","fields":[{"p":"/y"}]}]]}]},
      {"id":"cmp","label":{"fr-FR":"Conformité"},"roRoles":["Acheteur"],"rows":[{"columns":1,"blocks":[[{"id":"vies","type":"panel","fields":[{"p":"/z"}]},{"id":"gel","type":"note"}]]}]},
      {"label":{"fr-FR":"Sans id"},"rows":[{"columns":1,"blocks":[[{"id":"hist","type":"history"}]]}]}
     ],
     "variants":[{"id":"req","name":{"fr-FR":"Demande"},"roles":["Demandeur"],"tabs":[{"id":"my","label":{"fr-FR":"Ma demande"},"rows":[{"columns":1,"blocks":[[{"id":"q","type":"panel","fields":[{"p":"/a"}]}]]}]}]}]}
    """);
    Audience.Roles buyer = r -> { for (JsonNode x : r) if (x.asText().equals("Acheteur")) return true; return false; };
    Audience.Roles req = r -> { for (JsonNode x : r) if (x.asText().equals("Demandeur") || x.asText().equals("Acheteur")) return true; return false; };
    Audience.Roles none = r -> false;
    ObjectNode b = Audience.resolve(L, buyer, false);
    check("buyer variant", b.path("variant").asText(), "std");
    check("buyer tabs", tabs(b), "ov,com,cmp,t3");
    check("buyer blocks", blocks(b), "id1(ro),pay,buy,vies(ro),gel(ro),hist");
    check("buyer fields of id1", b.at("/tabs/0/rows/0/blocks/0/0/fields").size(), 2);
    check("buyer field c ro", b.at("/tabs/0/rows/0/blocks/0/0/fields/1/ro").asBoolean(), true);
    check("buyer row 0 single column (src hidden)", b.at("/tabs/0/rows/0/columns").asInt(), 1);
    check("no variants key", b.has("variants"), false);
    ObjectNode n = Audience.resolve(L, none, false);
    check("nobody tabs", tabs(n), "ov,cmp,t3");
    check("nobody blocks", blocks(n), "id1,vies,gel,hist");
    ObjectNode adm = Audience.resolve(L, none, true);
    check("admin sees all", blocks(adm), "id1,pay,src,buy,vies,gel,hist");
    ObjectNode rq = Audience.resolve(L, req, false);
    check("requester variant", rq.path("variant").asText() + ":" + tabs(rq), "req:my");
    // original untouched
    check("source untouched", L.at("/tabs/0/rows/0/blocks/0/0/fields").size(), 3);
    // personal
    ObjectNode p = Audience.resolve(L, buyer, false);
    Personal.apply(p, m.readTree("{\"tabs\":[\"cmp\",\"ov\"],\"defaultTab\":\"com\",\"hidden\":[\"pay\",\"hist\",\"zz\"],\"collapsed\":[\"gel\"],\"pinned\":[\"vies\",\"buy\"]}"), false);
    check("personal tabs (com emptied by the pin, so mine first)", tabs(p), "_mine,cmp,ov");
    check("personal blocks", blocks(p), "vies(ro),buy,gel(ro)(fold),id1(ro),pay");
    ObjectNode p5 = Audience.resolve(L, buyer, false);
    Personal.apply(p5, m.readTree("{\"tabs\":[\"cmp\",\"com\"],\"defaultTab\":\"com\",\"pinned\":[\"gel\"]}"), false);
    check("default tab first, then mine, listed tabs swapped in place", tabs(p5), "com,_mine,ov,cmp,t3");
    ObjectNode p2 = Audience.resolve(L, buyer, false);
    Personal.apply(p2, m.readTree("{\"hidden\":[\"buy\"],\"pinned\":[\"id1\"]}"), false);
    check("pinned first, emptied tab dropped", tabs(p2), "_mine,ov,cmp,t3");
    ObjectNode p3 = Audience.resolve(L, buyer, false);
    Personal.apply(p3, m.readTree("{\"hidden\":[\"buy\",\"id1\"],\"hiddenTabs\":[\"t3\"],\"collapsed\":[\"vies\"]}"), true);
    check("creating: nothing hidden", blocks(p3), "id1(ro),pay,buy,vies(ro),gel(ro),hist");
    ObjectNode p4 = (ObjectNode) Audience.resolve(L, buyer, false); p4.put("personal", false);
    Personal.apply(p4, m.readTree("{\"hidden\":[\"buy\"]}"), false);
    check("personal off: ignored", blocks(p4), "id1(ro),pay,buy,vies(ro),gel(ro),hist");
    // audit L2-25: a tab, row, block or field that is not an object is ignored, it does not break every form of the table
    JsonNode bad = m.readTree("{\"tabs\":[{\"label\":\"A\",\"rows\":[\"r\",{\"blocks\":[[\"b\",{\"id\":\"ok\",\"type\":\"panel\",\"fields\":[\"f\",{\"p\":\"/a\"}]}]]}]},\"x\"]}");
    ObjectNode rb = Audience.resolve(bad, none, false);
    check("non-object tab/row/block/field ignored", blocks(rb), "ok");
    check("non-object tab dropped", rb.path("tabs").size(), 1);
    ObjectNode ra = Audience.resolve(bad, none, true);
    check("non-object ignored for an administrator too", blocks(ra), "ok");
    System.out.println(fail == 0 ? "ALL OK" : fail + " FAILED"); if (fail > 0) System.exit(1);
  }
}
