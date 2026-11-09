package dev.elpsy.portfolio.admin;

import java.util.*;
import com.fasterxml.jackson.databind.*;
import dev.elpsy.portfolio.auth.AdminIdentity;
import dev.elpsy.portfolio.catalog.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class Taxonomy {
    public record Term(UUID id,String label,long version,Integer sortOrder,int references) {}
    public record Audit(UUID id,String kind,UUID termId,String actor,String action,JsonNode before,JsonNode after,String createdAt) {}
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final AdminProjects projects;
    public Taxonomy(@Qualifier("writerJdbc") JdbcTemplate db,ObjectMapper json,AdminProjects projects){this.db=db;this.json=json;this.projects=projects;}
    private static boolean category(String kind){if(!Set.of("categories","technologies").contains(kind))throw invalid();return kind.equals("categories");}
    private String table(String kind){return category(kind)?"category":"technology";}
    private String projection(String kind){
        return category(kind)?"SELECT t.id,t.label,t.version,t.sort_order,(SELECT count(*) FROM project p WHERE p.category_id=t.id) AS refs FROM category t"
                :"SELECT t.id,t.name,t.version,NULL::integer,(SELECT count(*) FROM project_technology p WHERE p.technology_id=t.id) AS refs FROM technology t";
    }
    private List<Term> query(String kind,String suffix,Object...args){return db.query(projection(kind)+suffix,(r,n)->new Term(r.getObject(1,UUID.class),r.getString(2),r.getLong(3),(Integer)r.getObject(4),r.getInt(5)),args);}
    private Term get(String kind,UUID id){var rows=query(kind," WHERE t.id=?",id);if(rows.isEmpty())throw new CatalogProblem(404,"TERM_NOT_FOUND","找不到分類或技術");return rows.getFirst();}
    @Transactional(transactionManager="writerTransactionManager",readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<Term> list(String kind){return query(kind,category(kind)?" ORDER BY t.sort_order,t.id":" ORDER BY t.name,t.id");}
    @Transactional(transactionManager="writerTransactionManager",timeout=10)
    public Term create(String kind,JsonNode command,AdminIdentity actor){
        boolean cat=category(kind);fields(command,cat?Set.of("label","sortOrder"):Set.of("label"));
        String label=label(command,cat);Integer order=cat?order(command):null;projects.lock(actor);
        if(db.queryForObject("SELECT count(*) FROM "+table(kind),Integer.class)>=500)throw new CatalogProblem(409,"TERM_LIMIT","分類或技術項目已達500筆上限");
        unique(kind,label,order,null);UUID id=UUID.randomUUID();
        if(cat)db.update("INSERT INTO category(id,label,sort_order) VALUES (?,?,?)",id,label,order);
        else db.update("INSERT INTO technology(id,name) VALUES (?,?)",id,label);
        var after=get(kind,id);append(db,json,kind,id,actor,"CREATE",null,after);return after;
    }
    @Transactional(transactionManager="writerTransactionManager",timeout=10)
    public Term update(String kind,UUID id,JsonNode command,AdminIdentity actor){
        boolean cat=category(kind);fields(command,cat?Set.of("label","sortOrder","expectedVersion"):Set.of("label","expectedVersion"));
        String label=label(command,cat);Integer order=cat?order(command):null;long expected=version(command);projects.lock(actor);
        var before=get(kind,id);expected(before,expected);unique(kind,label,order,id);
        if(before.label().equals(label) && Objects.equals(before.sortOrder(),order))return before;
        if(before.version()==Long.MAX_VALUE)throw conflict();
        var snapshots=new LinkedHashMap<String,AdminProjects.Managed>();
        if(!before.label().equals(label)) {
            String where=cat?"p.category_id=?":"EXISTS(SELECT 1 FROM project_technology pt WHERE pt.project_id=p.id AND pt.technology_id=?)";
            var slugs=db.query("SELECT p.slug FROM project p WHERE "+where+" ORDER BY p.slug LIMIT 101",(r,n)->r.getString(1),id);
            if(slugs.size()>100)throw new CatalogProblem(409,"TERM_RENAME_LIMIT","一次改名最多影響100個作品");
            for(String slug:slugs){projects.rowLock(slug);var old=projects.stored(slug);if(old.version()==Long.MAX_VALUE)throw conflict();snapshots.put(slug,old);}
        }
        if(cat)db.update("UPDATE category SET label=?,sort_order=?,version=version+1 WHERE id=?",label,order,id);
        else db.update("UPDATE technology SET name=?,version=version+1 WHERE id=?",label,id);
        for(var row:snapshots.entrySet()){
            db.update("UPDATE project SET version=version+1,updated_at=clock_timestamp() WHERE slug=?",row.getKey());
            projects.append(row.getKey(),actor,"UPDATE",row.getValue(),projects.stored(row.getKey()));
        }
        var after=get(kind,id);append(db,json,kind,id,actor,"UPDATE",before,after);return after;
    }
    @Transactional(transactionManager="writerTransactionManager",timeout=10)
    public void delete(String kind,UUID id,JsonNode command,AdminIdentity actor){
        category(kind);fields(command,Set.of("expectedVersion"));long version=version(command);projects.lock(actor);var before=get(kind,id);expected(before,version);
        if(before.references()>0)throw new CatalogProblem(409,"TERM_IN_USE","仍被作品引用，不能刪除");
        db.update("DELETE FROM "+table(kind)+" WHERE id=?",id);append(db,json,kind,id,actor,"DELETE",before,null);
    }
    private void unique(String kind,String label,Integer order,UUID own){
        String name=category(kind)?"label":"name";
        if(db.queryForObject("SELECT count(*) FROM "+table(kind)+" WHERE "+name+"=? AND (?::uuid IS NULL OR id<>?::uuid)",Integer.class,label,own,own)>0)
            throw new CatalogProblem(409,"TERM_EXISTS","分類或技術名稱已存在");
        if(category(kind) && db.queryForObject("SELECT count(*) FROM category WHERE sort_order=? AND (?::uuid IS NULL OR id<>?::uuid)",Integer.class,order,own,own)>0)
            throw new CatalogProblem(409,"TERM_ORDER_CONFLICT","分類排序位置已被使用");
    }
    static void append(JdbcTemplate db,ObjectMapper json,String kind,UUID id,AdminIdentity actor,String action,Term before,Term after){
        db.update("INSERT INTO taxonomy_audit(id,kind,term_id,actor,action,before_state,after_state) VALUES (?,?,?,?,?,?::jsonb,?::jsonb)",UUID.randomUUID(),kind,id,actor.getUsername(),action,before==null?null:json.valueToTree(before).toString(),after==null?null:json.valueToTree(after).toString());
    }
    @Transactional(transactionManager="writerTransactionManager",readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<Audit> audit(){return db.query("SELECT id,kind,term_id,actor,action,before_state::text,after_state::text,created_at::text FROM taxonomy_audit ORDER BY created_at DESC,id DESC LIMIT 50",(r,n)->{
        try{return new Audit(r.getObject(1,UUID.class),r.getString(2),r.getObject(3,UUID.class),r.getString(4),r.getString(5),r.getString(6)==null?null:json.readTree(r.getString(6)),r.getString(7)==null?null:json.readTree(r.getString(7)),r.getString(8));}
        catch(java.io.IOException e){throw new IllegalStateException("Invalid taxonomy audit",e);}
    });}
    private static void fields(JsonNode node,Set<String> keys){if(node==null || !node.isObject() || node.size()!=keys.size())throw invalid();node.fieldNames().forEachRemaining(k->{if(!keys.contains(k))throw invalid();});}
    private static String label(JsonNode node,boolean category){var value=node.get("label");if(value==null || !value.isTextual())throw invalid();String v=value.textValue().strip();if(v.isEmpty() || v.length()>(category?40:80) || v.indexOf('\0')>=0 || (category && v.equals("全部")))throw invalid();return v;}
    private static int order(JsonNode node){var v=node.get("sortOrder");if(v==null || !v.isIntegralNumber() || !v.canConvertToInt() || v.intValue()<0)throw invalid();return v.intValue();}
    private static long version(JsonNode node){var v=node.get("expectedVersion");if(v==null || !v.isIntegralNumber() || !v.canConvertToLong() || v.longValue()<1)throw invalid();return v.longValue();}
    private static void expected(Term term,long version){if(term.version()!=version)throw conflict();}
    private static CatalogProblem conflict(){return new CatalogProblem(409,"TERM_VERSION_CONFLICT","分類或技術已更新，請核對最新版本");}
    private static CatalogProblem invalid(){return new CatalogProblem(400,"INVALID_TERM","分類或技術欄位不正確");}
}
