package dev.elpsy.portfolio.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ProjectCatalog {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public ProjectCatalog(JdbcTemplate db, ObjectMapper json) { this.db = db; this.json = json; }

    public record Listing(List<ProjectSummary> items, int total, int catalogTotal, int page,int pageSize,int totalPages) {}

    private Project decode(String payload) {
        try { return json.readValue(payload, Project.class); }
        catch (java.io.IOException e) { throw new IllegalStateException("Stored catalog is invalid", e); }
    }

    private List<Project> query(String suffix, Object... args) {
        return db.query(ProjectProjection.SQL + " WHERE p.published " + suffix, (row, n) -> decode(row.getString("payload")), args);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Listing list(String query, String category, String featured) {
        return list(query,category,featured,PageRequest.parse("1","12"));
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Listing list(String query,String category,String featured,PageRequest page) {
        if(query.length()>120 || category.length()>40 || !Set.of("","true","false").contains(featured)
                || (!category.isBlank() && !Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM category WHERE label=?)",Boolean.class,category))))
            throw new CatalogProblem(400,"INVALID_QUERY","搜尋或篩選條件不正確");
        String filter="""
            AND (?='' OR c.label=?) AND (?='' OR p.featured=?)
            AND strpos(lower(concat_ws(' ',p.title,p.subtitle,p.summary,
                (SELECT string_agg(t.name,' ' ORDER BY pt.position) FROM project_technology pt
                JOIN technology t ON t.id=pt.technology_id WHERE pt.project_id=p.id))),?)>0
            """;
        Object[] args={category.isBlank()?"":category,category,featured,Boolean.parseBoolean(featured),query.strip().toLowerCase(Locale.ROOT)};
        int total=db.queryForObject("SELECT count(*) FROM project p JOIN category c ON c.id=p.category_id WHERE p.published "+filter,Integer.class,args);
        var bounded=new ArrayList<>(Arrays.asList(args));bounded.add(page.pageSize());bounded.add(page.offset());
        var items=query(filter+" ORDER BY p.sort_order,p.slug LIMIT ? OFFSET ?",bounded.toArray()).stream().map(ProjectSummary::from).toList();
        int catalogTotal=db.queryForObject("SELECT count(*) FROM project WHERE published",Integer.class);
        return new Listing(items,total,catalogTotal,page.page(),page.pageSize(),page.pages(total));
    }
    public record Next(ProjectSummary next) {}
    @Transactional(readOnly = true,isolation=Isolation.REPEATABLE_READ)
    public Next next(String slug) {
        var rows=db.query("SELECT sort_order FROM project WHERE slug=? AND published",(r,n)->r.getInt(1),slug);
        if(rows.isEmpty())throw new CatalogProblem(404,"PROJECT_NOT_FOUND","找不到這個專案");
        var next=query(" AND p.sort_order>? ORDER BY p.sort_order,p.slug LIMIT 1",rows.getFirst());
        if(next.isEmpty())next=query(" AND p.slug<>? ORDER BY p.sort_order,p.slug LIMIT 1",slug);
        return new Next(next.isEmpty()?null:ProjectSummary.from(next.getFirst()));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Project detail(String slug) {
        var projects = query(" AND p.slug=?", slug);
        if (projects.isEmpty()) throw new CatalogProblem(404, "PROJECT_NOT_FOUND", "找不到這個專案");
        return projects.getFirst();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<String> categories() {
        return db.query("SELECT c.label FROM category c WHERE EXISTS(SELECT 1 FROM project p "
                + "WHERE p.category_id=c.id AND p.published) ORDER BY c.sort_order,c.label",
                (row, n) -> row.getString(1));
    }

    @Transactional(readOnly = true)
    public void checkDatabase() { db.queryForObject("SELECT 1", Integer.class); }
}
