import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: "/admin/taxonomy", component: () => import("../views/AdminTaxonomyView.vue"), meta: { title: "分類與技術管理" } },
    { path: "/admin/password", component: () => import("../views/AdminPasswordView.vue"), meta: { title: "更改我的密碼" } },
    { path: "/admin/accounts", component: () => import("../views/AdminAccountsView.vue"), meta: { title: "帳號管理" } },
    { path: '/admin/projects/:slug/media', component: () => import('../views/AdminProjectMediaView.vue'), meta: { title: '圖片管理' } },
    { path: '/admin/projects', component: () => import('../views/AdminProjectsView.vue'), meta: { title: '作品管理' } },
    { path: '/admin/projects/new', component: () => import('../views/AdminProjectEditView.vue'), meta: { title: '新增作品' } },
    { path: '/admin/projects/:slug/audit', component: () => import('../views/AdminProjectAuditView.vue'), meta: { title: '稽核紀錄' } },
    { path: '/admin/projects/:slug', component: () => import('../views/AdminProjectEditView.vue'), meta: { title: '編輯作品' } },
    { path: '/admin/login', name: 'admin-login', component: () => import('../views/AdminLoginView.vue'), meta: { title: '管理登入' } },
    { path: '/admin', name: 'admin', component: () => import('../views/AdminView.vue'), meta: { title: '管理概覽' } },
    {
      path: '/',
      name: 'home',
      component: () => import('../views/HomeView.vue'),
      meta: { title: '首頁' },
    },
    {
      path: '/projects',
      name: 'projects',
      component: () => import('../views/ProjectsView.vue'),
      meta: { title: '所有專案' },
    },
    {
      path: '/projects/:slug',
      name: 'project-detail',
      component: () => import('../views/ProjectDetailView.vue'),
      meta: { title: '專案介紹' },
    },
    {
      path: '/about',
      name: 'about',
      component: () => import('../views/AboutView.vue'),
      meta: { title: '關於我' },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('../views/NotFoundView.vue'),
      meta: { title: '找不到頁面' },
    },
  ].filter(route => import.meta.env.VITE_PUBLIC_SHOWCASE !== 'true' || !route.path.startsWith('/admin')),
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash, top: 100 }
    return { top: 0 }
  },
})

router.afterEach((to) => {
  document.title = `${String(to.meta.title)} — Devfolio`
})
export default router
