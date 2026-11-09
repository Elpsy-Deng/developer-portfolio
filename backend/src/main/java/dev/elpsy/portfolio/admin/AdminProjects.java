package dev.elpsy.portfolio.admin;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import dev.elpsy.portfolio.auth.AdminIdentity;
import dev.elpsy.portfolio.catalog.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AdminProjects {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final ImageCatalog images;
    public AdminProjects(@Qualifier("writerJdbc") JdbcTemplate db,ObjectMapper json,ImageCatalog images) { this.db=db; this.json=json; this.images=images; }
    public record Managed(Project project,long version,boolean published,int sortOrder) {}
    public record Row(String slug,String title,String category,String status,boolean published,boolean featured,long version,int sortOrder) {}
    public record Change(ProjectContent content,Long expectedVersion,Boolean published,Integer sortOrder) {}
    public record Delete(Long expectedVersion) {}
    public record Audit(UUID id,String actor,String action,Object before,Object after,String createdAt) {}
    private String encode(Object value) {
        try { return json.writeValueAsString(value); } catch(JsonProcessingException e) { throw new IllegalStateException("Invalid stored project",e); }
    }
    private <T> T decode(String value,Class<T> type) {
        try { return value==null?null:json.readValue(value,type); } catch(JsonProcessingException e) { throw new IllegalStateException("Invalid stored project",e); }
    }
    @Transactional(transactionManager="writerTransactionManager",readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<Row> list() {
        return db.query("SELECT p.slug,p.title,c.label,p.status,p.published,p.featured,p.version,p.sort_order FROM project p JOIN category c ON c.id=p.category_id ORDER BY p.sort_order,p.slug",
                (r,n)->new Row(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getBoolean(5),r.getBoolean(6),r.getLong(7),r.getInt(8)));
    }
    public record Listing(List<Row> items,int total,int page,int pageSize,int totalPages) {}
    @Transactional(transactionManager="writerTransactionManager",readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Listing list(String q,String category,String published,PageRequest page) {
        if(q.length()>120 || category.length()>40 || !Set.of("","true","false").contains(published))throw new CatalogProblem(400,"INVALID_QUERY","查詢條件不正確");
        String filter=" WHERE (?='' OR c.label=?) AND (?='' OR p.published=?) AND strpos(lower(concat_ws(' ',p.slug,p.title,p.subtitle,p.summary,(SELECT string_agg(t.name,' ' ORDER BY pt.position) FROM project_technology pt JOIN technology t ON t.id=pt.technology_id WHERE pt.project_id=p.id))),?)>0";
        Object[] args={category,category,published,Boolean.parseBoolean(published),q.strip().toLowerCase(Locale.ROOT)};
        String from=" FROM project p JOIN category c ON c.id=p.category_id";
        int total=db.queryForObject("SELECT count(*)"+from+filter,Integer.class,args);
        var bounded=new ArrayList<>(Arrays.asList(args));bounded.add(page.pageSize());bounded.add(page.offset());
        var items=db.query("SELECT p.slug,p.title,c.label,p.status,p.published,p.featured,p.version,p.sort_order"+from+filter+" ORDER BY p.sort_order,p.slug LIMIT ? OFFSET ?",(r,n)->new Row(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getBoolean(5),r.getBoolean(6),r.getLong(7),r.getInt(8)),bounded.toArray());
        return new Listing(items,total,page.page(),page.pageSize(),page.pages(total));
    }
    @Transactional(transactionManager="writerTransactionManager",readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,List<String>> options() {
        return Map.of("categories",db.query("SELECT label FROM category ORDER BY sort_order",(r,n)->r.getString(1)),
                "technologies",db.query("SELECT name FROM technology ORDER BY name",(r,n)->r.getString(1)));
    }
    @Transactional(transactionManager="writerTransactionManager",readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Managed detail(String slug) { return stored(slug); }
    Managed stored(String slug) {
        var rows=db.query(ProjectProjection.SQL+" WHERE p.slug=?",(r,n)->decode(r.getString("payload"),Project.class),slug);
        if(rows.isEmpty()) throw new CatalogProblem(404,"PROJECT_NOT_FOUND","找不到這個作品");
        var meta=db.queryForMap("SELECT version,published,sort_order FROM project WHERE slug=?",slug);
        return new Managed(rows.getFirst(),((Number)meta.get("version")).longValue(),(boolean)meta.get("published"),((Number)meta.get("sort_order")).intValue());
    }
    @Transactional(transactionManager="writerTransactionManager",readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<Audit> audit(String slug) {
        return db.query("SELECT id,actor,action,before_snapshot::text,after_snapshot::text,created_at::text FROM project_audit WHERE project_slug=? ORDER BY created_at DESC,id DESC LIMIT 50",
                (r,n)->new Audit(r.getObject(1,UUID.class),r.getString(2),r.getString(3),decode(r.getString(4),Object.class),decode(r.getString(5),Object.class),r.getString(6)),slug);
    }
    void lock(AdminIdentity actor) {
        db.queryForObject("SELECT pg_advisory_xact_lock(681227041)",Object.class);
        if(db.queryForObject("SELECT count(*) FROM admin_account WHERE username=? AND enabled AND role='ADMIN' AND version=?",Integer.class,actor.getUsername(),actor.version())!=1)
            throw new CatalogProblem(401,"UNAUTHENTICATED","請重新登入");
        // Low-volume administration: serialize order allocation, avoiding races and multi-row lock deadlocks.
    }
    private void validate(Change change,boolean creating,String slug) {
        if(change==null || change.content()==null || change.published()==null) throw new CatalogProblem(400,"INVALID_PROJECT","作品欄位不完整");
        change.content().validate();
        if(!change.content().slug().equals(slug)) throw new CatalogProblem(400,"IMMUTABLE_SLUG","既有作品網址代號不能更改");
        if(creating ? change.expectedVersion()!=null : change.expectedVersion()==null || change.expectedVersion()<1)
            throw new CatalogProblem(400,"INVALID_VERSION","請提供正確作品版本");
        if(change.sortOrder()!=null && change.sortOrder()<0) throw new CatalogProblem(400,"INVALID_ORDER","排序位置不可為負數");
    }
    void rowLock(String slug) {
        if(db.query("SELECT version FROM project WHERE slug=? FOR UPDATE",(r,n)->r.getLong(1),slug).isEmpty())
            throw new CatalogProblem(404,"PROJECT_NOT_FOUND","找不到這個作品");
    }
    void expected(long actual,Long expected) {
        if(expected==null || expected<1) throw new CatalogProblem(400,"INVALID_VERSION","請提供作品版本");
        if(actual!=expected) throw new CatalogProblem(409,"VERSION_CONFLICT","作品已被更新，請核對最新版本後再儲存");
    }
    private UUID category(String label) {
        var values=db.query("SELECT id FROM category WHERE label=?",(r,n)->r.getObject(1,UUID.class),label);
        if(values.isEmpty()) throw new CatalogProblem(400,"INVALID_CATEGORY","分類不正確");
        return values.getFirst();
    }
    private int order(Integer desired,String slug) {
        long allocated=desired==null?db.queryForObject("SELECT COALESCE(max(sort_order)::bigint,-1)+1 FROM project",Long.class):desired;
        if(allocated>Integer.MAX_VALUE) throw new CatalogProblem(409,"ORDER_CONFLICT","排序位置已達上限，請調整現有排序");
        int value=(int)allocated;
        if(db.queryForObject("SELECT count(*) FROM project WHERE sort_order=? AND slug<>?",Integer.class,value,slug)>0)
            throw new CatalogProblem(409,"ORDER_CONFLICT","排序位置已被使用，請選擇其他位置");
        return value;
    }
    @Transactional(transactionManager="writerTransactionManager")
    public Managed create(Change change,AdminIdentity actor) {
        if(change==null || change.content()==null) throw new CatalogProblem(400,"INVALID_PROJECT","作品欄位不完整");
        String slug=change.content().slug(); validate(change,true,slug); lock(actor);
        if(db.queryForObject("SELECT EXISTS(SELECT 1 FROM project WHERE slug=?) OR EXISTS(SELECT 1 FROM project_audit WHERE project_slug=?)",Boolean.class,slug,slug))
            throw new CatalogProblem(409,"SLUG_EXISTS","作品網址代號已使用，請選擇新代號");
        UUID id=UUID.randomUUID(); var c=change.content();
        db.update("""
            INSERT INTO project(id,slug,category_id,title,subtitle,status,status_note,summary,description,year,role,duration,featured,published,theme,technology_label,sort_order,features,planned_goals,verification,limitations,challenges,architecture,architecture_description,github_url,live_demo_url,evidence_checked_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb,?::jsonb,?::jsonb,?::jsonb,?,?,?,?::date)
            """,id,slug,category(c.category()),c.title(),c.subtitle(),c.status(),c.statusNote(),c.summary(),c.description(),c.year(),c.role(),c.duration(),c.featured(),change.published(),c.theme(),c.technologyLabel(),order(change.sortOrder(),slug),
                encode(c.features()),nullable(c.plannedGoals()),nullable(c.verification()),nullable(c.limitations()),encode(c.challenges()),encode(c.architecture()),c.architectureDescription(),c.githubUrl(),c.liveDemoUrl(),c.evidenceCheckedAt());
        relations(id,c,actor); var after=stored(slug); append(slug,actor,"CREATE",null,after); return after;
    }
    @Transactional(transactionManager="writerTransactionManager")
    public Managed update(String slug,Change change,AdminIdentity actor) {
        validate(change,false,slug); lock(actor);
        rowLock(slug); var before=stored(slug); expected(before.version(),change.expectedVersion()); var c=change.content();
        int position=order(change.sortOrder()==null?before.sortOrder():change.sortOrder(),slug);
        db.update("""
            UPDATE project SET category_id=?,title=?,subtitle=?,status=?,status_note=?,summary=?,description=?,year=?,role=?,duration=?,featured=?,published=?,theme=?,technology_label=?,sort_order=?,features=?::jsonb,planned_goals=?::jsonb,verification=?::jsonb,limitations=?::jsonb,challenges=?::jsonb,architecture=?::jsonb,architecture_description=?,github_url=?,live_demo_url=?,evidence_checked_at=?::date,version=version+1,updated_at=clock_timestamp() WHERE slug=?
            """,category(c.category()),c.title(),c.subtitle(),c.status(),c.statusNote(),c.summary(),c.description(),c.year(),c.role(),c.duration(),c.featured(),change.published(),c.theme(),c.technologyLabel(),position,
                encode(c.features()),nullable(c.plannedGoals()),nullable(c.verification()),nullable(c.limitations()),encode(c.challenges()),encode(c.architecture()),c.architectureDescription(),c.githubUrl(),c.liveDemoUrl(),c.evidenceCheckedAt(),slug);
        UUID id=db.queryForObject("SELECT id FROM project WHERE slug=?",UUID.class,slug); relations(id,c,actor);
        var after=stored(slug); append(slug,actor,"UPDATE",before,after); return after;
    }
    @Transactional(transactionManager="writerTransactionManager")
    public void delete(String slug,Delete command,AdminIdentity actor) {
        if(command==null) throw new CatalogProblem(400,"INVALID_VERSION","請提供作品版本");
        lock(actor); rowLock(slug); var before=stored(slug); expected(before.version(),command.expectedVersion());
        UUID id=db.queryForObject("SELECT id FROM project WHERE slug=?",UUID.class,slug);
        db.update("DELETE FROM project_technology WHERE project_id=?",id);
        db.update("DELETE FROM project_media WHERE project_id=?",id);
        db.update("DELETE FROM project_evidence WHERE project_id=?",id);
        db.update("DELETE FROM project WHERE id=?",id);
        images.retireOrphans();
        append(slug,actor,"DELETE",before,null);
    }
    private String nullable(Object value) { return value==null?null:encode(value); }
    private void relations(UUID id,ProjectContent c,AdminIdentity actor) {
        db.update("DELETE FROM project_technology WHERE project_id=?",id);
        for(int position=0;position<c.technologies().size();position++) {
            String name=c.technologies().get(position);
            if(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM technology WHERE name=?)",Boolean.class,name))
                    && db.queryForObject("SELECT count(*) FROM technology",Integer.class)>=500)
                throw new CatalogProblem(409,"TERM_LIMIT","技術項目已達500筆上限");
            UUID created=UUID.randomUUID();
            if(db.update("INSERT INTO technology(id,name) VALUES (?,?) ON CONFLICT(name) DO NOTHING",created,name)==1) {
                var term=new Taxonomy.Term(created,name,1,null,0);
                Taxonomy.append(db,json,"technologies",created,actor,"CREATE",null,term);
            }
            UUID technology=db.queryForObject("SELECT id FROM technology WHERE name=?",UUID.class,name);
            db.update("INSERT INTO project_technology(project_id,technology_id,position) VALUES (?,?,?)",id,technology,position);
        }
        db.update("DELETE FROM project_evidence WHERE project_id=?",id);
        if(c.evidenceLinks()!=null) for(int position=0;position<c.evidenceLinks().size();position++) {
            var link=c.evidenceLinks().get(position);
            db.update("INSERT INTO project_evidence(project_id,position,label,url) VALUES (?,?,?,?)",id,position,link.label(),link.url());
        }
    }
    void append(String slug,AdminIdentity actor,String action,Managed before,Managed after) {
        db.update("INSERT INTO project_audit(id,project_slug,actor,action,before_snapshot,after_snapshot) VALUES (?,?,?,?,?::jsonb,?::jsonb)",
                UUID.randomUUID(),slug,actor.getUsername(),action,nullable(before),nullable(after));
    }
}
