package dev.jackteng.portfolio.admin;

import java.util.*;
import dev.jackteng.portfolio.auth.AdminIdentity;
import dev.jackteng.portfolio.catalog.CatalogProblem;
import dev.jackteng.portfolio.media.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaProjects {
    private final JdbcTemplate db;
    private final AdminProjects projects;
    private final ImageCatalog catalog;
    private final ImageNormalizer normalizer;
    private final ImageStore store;
    public MediaProjects(@Qualifier("writerJdbc") JdbcTemplate db,AdminProjects projects,ImageCatalog catalog,ImageNormalizer normalizer,ImageStore store) {
        this.db=db;this.projects=projects;this.catalog=catalog;this.normalizer=normalizer;this.store=store;
    }
    public record Media(UUID id,String kind,int position,String title,String description,String src,String alt,boolean managed,Integer width,Integer height,Long bytes) {}
    public record Metadata(Long expectedVersion,String title,String description,String alt,Integer position) {}
    @Transactional(transactionManager="writerTransactionManager",readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<Media> list(String slug) {
        projects.stored(slug);
        return db.query("""
            SELECT m.id,m.kind,m.position,m.title,m.description,m.src,m.alt,m.asset_id IS NOT NULL,a.width,a.height,a.bytes
            FROM project_media m JOIN project p ON p.id=m.project_id LEFT JOIN image_asset a ON a.id=m.asset_id
            WHERE p.slug=? ORDER BY m.kind,m.position,m.id
            """,(r,n)->new Media(r.getObject(1,UUID.class),r.getString(2),r.getInt(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getBoolean(8),r.getObject(9,Integer.class),r.getObject(10,Integer.class),r.getObject(11,Long.class)),slug);
    }
    private UUID projectId(String slug) { return db.queryForObject("SELECT id FROM project WHERE slug=?",UUID.class,slug); }
    private void fields(String kind,String title,String description,String alt,boolean image) {
        if(!Set.of("COVER","SCREENSHOT").contains(kind) || title==null || title.length()>160 || description==null || description.length()>5000
                || alt==null || alt.length()>300 || (kind.equals("SCREENSHOT") && title.isBlank()) || (image && alt.isBlank())
                || title.indexOf('\0')>=0 || description.indexOf('\0')>=0 || alt.indexOf('\0')>=0)
            throw new CatalogProblem(400,"INVALID_MEDIA","請核對圖片標題、說明與替代文字");
    }
    private AdminProjects.Managed before(String slug,Long version,AdminIdentity actor) {
        projects.lock(actor);projects.rowLock(slug);var current=projects.stored(slug);projects.expected(current.version(),version);return current;
    }
    private AdminProjects.Managed finish(String slug,AdminIdentity actor,AdminProjects.Managed before) {
        db.update("UPDATE project SET version=version+1,updated_at=clock_timestamp() WHERE slug=?",slug);
        catalog.retireOrphans();var after=projects.stored(slug);projects.append(slug,actor,"UPDATE",before,after);return after;
    }
    @Transactional(transactionManager="writerTransactionManager",timeout=30)
    public AdminProjects.Managed upload(String slug,Long expected,String kind,String title,String description,String alt,MultipartFile file,AdminIdentity actor) {
        fields(kind,title,description,alt,true);var previous=before(slug,expected,actor);UUID project=projectId(slug);
        int position=0;
        if(kind.equals("SCREENSHOT")) {
            if(db.queryForObject("SELECT count(*) FROM project_media WHERE project_id=? AND kind='SCREENSHOT'",Integer.class,project)>=20)
                throw new CatalogProblem(400,"MEDIA_LIMIT","每個作品最多20張截圖");
            long next=db.queryForObject("SELECT COALESCE(max(position)::bigint,-1)+1 FROM project_media WHERE project_id=? AND kind='SCREENSHOT'",Long.class,project);
            if(next>Integer.MAX_VALUE) throw new CatalogProblem(409,"MEDIA_ORDER_CONFLICT","截圖排序位置已達上限");
            position=(int)next;
        }
        var image=normalizer.normalize(file);UUID asset=UUID.randomUUID();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            public void afterCompletion(int status) {
                if(status!=STATUS_COMMITTED) try { store.delete(asset,image.format()); }
                catch(ImageStorageProblem ignored) { /* Crash/IO residue is reclaimed after a24h orphan grace period. */ }
            }
        });
        store.put(asset,image.format(),image.bytes());
        db.update("INSERT INTO image_asset(id,format,mime,width,height,bytes,sha256) VALUES (?,?,?,?,?,?,?)",asset,image.format(),image.mime(),image.width(),image.height(),image.bytes().length,image.sha256());
        if(kind.equals("COVER")) db.update("DELETE FROM project_media WHERE project_id=? AND kind='COVER'",project);
        db.update("INSERT INTO project_media(id,project_id,kind,position,title,description,src,alt,asset_id) VALUES (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),project,kind,position,title,description,"/api/public/media/"+asset,alt,asset);
        return finish(slug,actor,previous);
    }
    private Media selected(String slug,UUID id) {
        return list(slug).stream().filter(m->m.id().equals(id)).findFirst().orElseThrow(()->new CatalogProblem(404,"MEDIA_NOT_FOUND","找不到這張圖片"));
    }
    @Transactional(transactionManager="writerTransactionManager")
    public AdminProjects.Managed update(String slug,UUID id,Metadata command,AdminIdentity actor) {
        var previous=before(slug,command.expectedVersion(),actor);var media=selected(slug,id);
        fields(media.kind(),command.title(),command.description(),command.alt(),media.src()!=null);
        if(command.position()==null || command.position()<0 || (media.kind().equals("COVER") && command.position()!=0))
            throw new CatalogProblem(400,"INVALID_MEDIA","圖片排序不正確");
        if(db.queryForObject("SELECT count(*) FROM project_media WHERE project_id=? AND kind=? AND position=? AND id<>?",Integer.class,projectId(slug),media.kind(),command.position(),id)>0)
            throw new CatalogProblem(409,"MEDIA_ORDER_CONFLICT","圖片排序位置已被使用");
        db.update("UPDATE project_media SET title=?,description=?,alt=?,position=? WHERE id=?",command.title(),command.description(),command.alt(),command.position(),id);
        return finish(slug,actor,previous);
    }
    @Transactional(transactionManager="writerTransactionManager")
    public AdminProjects.Managed remove(String slug,UUID id,AdminProjects.Delete command,AdminIdentity actor) {
        var previous=before(slug,command.expectedVersion(),actor);selected(slug,id);
        db.update("DELETE FROM project_media WHERE id=?",id);return finish(slug,actor,previous);
    }
}
