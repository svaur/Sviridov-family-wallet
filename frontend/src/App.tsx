import { Routes, Route, Link } from 'react-router-dom'
import HomePage from './pages/HomePage'

export default function App() {
  return (
    <div className="app">
      <header className="app-header">
        <Link to="/">Sviridov Family Wallet</Link>
      </header>
      <main className="app-main">
        <Routes>
          <Route path="/" element={<HomePage />} />
        </Routes>
      </main>
    </div>
  )
}
