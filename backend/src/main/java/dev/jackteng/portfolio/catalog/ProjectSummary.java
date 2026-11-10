package dev.jackteng.portfolio.catalog;

import java.util.List;

public record ProjectSummary(
        String slug, String title, String subtitle, String category, String status,
        String summary, String year, boolean featured, String theme,
        List<String> technologies, String technologyLabel, Project.CoverImage coverImage) {
    public static ProjectSummary from(Project project) {
        return new ProjectSummary(project.slug(), project.title(), project.subtitle(), project.category(),
                project.status(), project.summary(), project.year(), project.featured(), project.theme(),
                project.technologies(), project.technologyLabel(), project.coverImage());
    }
}
