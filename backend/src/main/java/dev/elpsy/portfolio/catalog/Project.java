package dev.elpsy.portfolio.catalog;

import java.util.List;

public record Project(
        String slug, String title, String subtitle, String category, String status,
        String statusNote, String summary, String description, String year, String role,
        String duration, boolean featured, String theme, List<String> technologies,
        String technologyLabel, List<String> features, List<String> plannedGoals,
        List<String> verification, List<String> limitations, List<Challenge> challenges,
        List<String> architecture, String architectureDescription, List<Screenshot> screenshots,
        CoverImage coverImage, String githubUrl, String liveDemoUrl, String localDemoUrl,
        List<EvidenceLink> evidenceLinks, String evidenceCheckedAt) {
    public record Challenge(String title, String description) {}
    public record Screenshot(String title, String description, String src, String alt) {}
    public record CoverImage(String src, String alt) {}
    public record EvidenceLink(String label, String url) {}
}
