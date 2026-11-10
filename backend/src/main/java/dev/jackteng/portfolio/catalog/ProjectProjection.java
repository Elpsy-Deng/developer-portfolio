package dev.jackteng.portfolio.catalog;

/** Shared DTO projection; authorization predicates belong to each caller. */
public final class ProjectProjection {
    private ProjectProjection() {}
    public static final String SQL = """
        SELECT jsonb_build_object(
          'slug',p.slug,'title',p.title,'subtitle',p.subtitle,'category',c.label,
          'status',p.status,'statusNote',p.status_note,'summary',p.summary,
          'description',p.description,'year',p.year,'role',p.role,'duration',p.duration,
          'featured',p.featured,'theme',p.theme,'technologyLabel',p.technology_label,
          'features',p.features,'plannedGoals',p.planned_goals,'verification',p.verification,
          'limitations',p.limitations,'challenges',p.challenges,'architecture',p.architecture,
          'architectureDescription',p.architecture_description,
          'githubUrl',p.github_url,'liveDemoUrl',p.live_demo_url,'localDemoUrl',p.local_demo_url,
          'evidenceCheckedAt',to_char(p.evidence_checked_at,'YYYY-MM-DD'),
          'technologies',COALESCE((SELECT jsonb_agg(t.name ORDER BY pt.position)
              FROM project_technology pt JOIN technology t ON t.id=pt.technology_id
              WHERE pt.project_id=p.id),'[]'::jsonb),
          'coverImage',(SELECT jsonb_build_object('src',m.src,'alt',m.alt)
              FROM project_media m WHERE m.project_id=p.id AND m.kind='COVER'),
          'screenshots',COALESCE((SELECT jsonb_agg(jsonb_build_object('title',m.title,
              'description',m.description,'src',m.src,'alt',m.alt) ORDER BY m.position)
              FROM project_media m WHERE m.project_id=p.id AND m.kind='SCREENSHOT'),'[]'::jsonb),
          'evidenceLinks',(SELECT jsonb_agg(jsonb_build_object('label',e.label,'url',e.url)
              ORDER BY e.position) FROM project_evidence e WHERE e.project_id=p.id)
        )::text AS payload
        FROM project p JOIN category c ON c.id=p.category_id
        """;

}
