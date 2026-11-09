export type ProjectCategory = string
export type ProjectTheme = 'forest' | 'blue' | 'peach' | 'violet'
export type ProjectStatus = 'Planned' | 'In Progress' | 'Completed'

export interface ProjectScreenshot {
  title: string
  description: string
  src?: string
  alt?: string
}

export interface Project {
  slug: string
  title: string
  subtitle: string
  category: ProjectCategory
  status: ProjectStatus
  statusNote: string
  summary: string
  description: string
  year: string
  role: string
  duration: string
  featured: boolean
  theme: ProjectTheme
  technologies: string[]
  technologyLabel: string
  features: string[]
  plannedGoals?: string[]
  verification?: string[]
  limitations?: string[]
  evidenceLinks?: { label: string; url: string }[]
  evidenceCheckedAt?: string
  challenges: { title: string; description: string }[]
  architecture: string[]
  architectureDescription?: string
  screenshots: ProjectScreenshot[]
  coverImage?: { src: string; alt: string }
  githubUrl: string | null
  liveDemoUrl: string | null
  localDemoUrl?: string
}

export type ProjectSummary = Pick<Project,
  'slug' | 'title' | 'subtitle' | 'category' | 'status' | 'summary' | 'year' |
  'featured' | 'theme' | 'technologies' | 'technologyLabel' | 'coverImage'
>
