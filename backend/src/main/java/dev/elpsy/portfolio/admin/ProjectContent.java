package dev.elpsy.portfolio.admin;

import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import dev.elpsy.portfolio.catalog.Project;
import dev.elpsy.portfolio.catalog.CatalogProblem;

/** Editable text, links and tags. Existing media and local-demo metadata are preserved. */
public record ProjectContent(String slug, String title, String subtitle, String category, String status,
        String statusNote, String summary, String description, String year, String role, String duration,
        Boolean featured, String theme, String technologyLabel, List<String> technologies, List<String> features,
        List<String> plannedGoals, List<String> verification, List<String> limitations,
        List<Project.Challenge> challenges, List<String> architecture, String architectureDescription,
        String githubUrl, String liveDemoUrl, List<Project.EvidenceLink> evidenceLinks, String evidenceCheckedAt) {
    public void validate() {
        text("slug", slug, 80, true);
        if (slug.equals("new") || !slug.matches("[a-z0-9]+(-[a-z0-9]+)*")) invalid("slug");
        text("title",title,160,true); text("subtitle",subtitle,300,false); text("category",category,40,true);
        text("status",status,20,true); text("statusNote",statusNote,1000,false);
        text("summary",summary,2000,true); text("description",description,20000,true);
        text("year",year,40,false); text("role",role,160,false); text("duration",duration,160,false);
        text("technologyLabel",technologyLabel,160,false);
        if (featured==null || !Set.of("Planned","In Progress","Completed").contains(status)
                || theme==null || !Set.of("forest","blue","peach","violet").contains(theme)) invalid("status/theme/featured");
        lines("technologies",technologies,30,80,true);
        if (technologies.stream().anyMatch(v -> !v.equals(v.strip())) || new HashSet<>(technologies).size()!=technologies.size()) invalid("technologies");
        lines("features",features,50,2000,true); lines("architecture",architecture,30,300,true);
        lines("plannedGoals",plannedGoals,50,2000,false); lines("verification",verification,50,2000,false); lines("limitations",limitations,50,2000,false);
        if (architectureDescription!=null) text("architectureDescription",architectureDescription,10000,false);
        if (challenges==null || challenges.size()>30 || challenges.stream().anyMatch(Objects::isNull)) invalid("challenges");
        for (var v:challenges) { text("challenge.title",v.title(),160,true); text("challenge.description",v.description(),5000,true); }
        https("githubUrl",githubUrl); https("liveDemoUrl",liveDemoUrl);
        if (evidenceLinks!=null) {
            if (evidenceLinks.size()>30 || evidenceLinks.stream().anyMatch(Objects::isNull)) invalid("evidenceLinks");
            for (var v:evidenceLinks) { text("evidence.label",v.label(),160,true); text("evidence.url",v.url(),2048,true); https("evidence.url",v.url()); }
            if (!evidenceLinks.isEmpty() && evidenceCheckedAt==null) invalid("evidenceCheckedAt");
        }
        if (evidenceCheckedAt!=null) {
            try { if (evidenceCheckedAt.length()!=10) invalid("evidenceCheckedAt"); if (!LocalDate.parse(evidenceCheckedAt).toString().equals(evidenceCheckedAt)) invalid("evidenceCheckedAt"); }
            catch (java.time.DateTimeException e) { invalid("evidenceCheckedAt"); }
        }
    }
    private static void lines(String name,List<String> values,int count,int max,boolean required) {
        if (values==null) { if(required) invalid(name); return; }
        if (values.size()>count) invalid(name);
        for(var value:values) text(name,value,max,true);
    }
    private static void text(String name,String value,int max,boolean required) {
        if (value==null) { invalid(name); return; }
        if (value.length()>max || value.indexOf('\0')>=0 || (required && value.isBlank())) invalid(name);
    }
    private static void https(String name,String value) {
        if(value==null) return;
        text(name,value,2048,true);
        try { var uri=new URI(value); if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null) invalid(name); }
        catch(java.net.URISyntaxException e) { invalid(name); }
    }
    private static void invalid(String field) { throw new CatalogProblem(400,"INVALID_PROJECT","作品欄位不正確："+field); }
}
