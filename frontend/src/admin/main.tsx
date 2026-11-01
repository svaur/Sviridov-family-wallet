import React from 'react'
import ReactDOM from 'react-dom/client'
import { HashRouter } from 'react-router-dom'
import AdminApp from './AdminApp'
import '../index.css'

// The admin panel is a separate SPA (separate HTML entry + bundle + session
// attribute). HashRouter keeps the sub-routes client-side, so the only thing
// the web server has to do is serve admin.html at /admin — no rewrites for
// /admin/users etc. In dev it is reachable at /admin.html.
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <HashRouter>
      <AdminApp />
    </HashRouter>
  </React.StrictMode>,
)
