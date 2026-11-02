import { createRouter, createWebHashHistory } from 'vue-router'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      name: 'board',
      component: () => import('./pages/BoardPage.vue'),
    },
    {
      path: '/deliveries',
      name: 'deliveries',
      component: () => import('./pages/DeliveriesPage.vue'),
    },
    {
      path: '/deliveries/new',
      name: 'delivery-new',
      component: () => import('./pages/DeliveryFormPage.vue'),
    },
    {
      path: '/deliveries/:id(\\d+)',
      name: 'delivery-detail',
      component: () => import('./pages/DeliveryDetailPage.vue'),
    },
    {
      path: '/deliveries/:id(\\d+)/edit',
      name: 'delivery-edit',
      component: () => import('./pages/DeliveryFormPage.vue'),
    },
    {
      path: '/estimate',
      name: 'estimate',
      component: () => import('./pages/EstimatePage.vue'),
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

export default router
