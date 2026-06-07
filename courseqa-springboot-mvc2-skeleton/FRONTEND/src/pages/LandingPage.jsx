import { useEffect, useState } from 'react'
import FeatureOverview from '../components/landing/FeatureOverview.jsx'
import LandingFooter from '../components/landing/LandingFooter.jsx'
import LandingHeader from '../components/landing/LandingHeader.jsx'
import LandingHero from '../components/landing/LandingHero.jsx'
import ResearchSection from '../components/landing/ResearchSection.jsx'
import WorkflowTimeline from '../components/landing/WorkflowTimeline.jsx'

function LandingPage() {
  const [activeTheme, setActiveTheme] = useState('hero')

  useEffect(() => {
    const sections = Array.from(
      document.querySelectorAll('[data-section-theme]'),
    )

    const observer = new IntersectionObserver(
      (entries) => {
        const visibleEntry = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0]

        if (visibleEntry?.target instanceof HTMLElement) {
          setActiveTheme(visibleEntry.target.dataset.sectionTheme ?? 'hero')
        }
      },
      {
        rootMargin: '-35% 0px -45% 0px',
        threshold: [0.12, 0.24, 0.36, 0.48, 0.6],
      },
    )

    sections.forEach((section) => observer.observe(section))

    return () => observer.disconnect()
  }, [])

  return (
    <div className="min-h-screen bg-background font-body text-foreground">
      <LandingHeader activeTheme={activeTheme} />
      <main>
        <LandingHero />
        <FeatureOverview />
        <WorkflowTimeline />
        <ResearchSection />
      </main>
      <LandingFooter />
    </div>
  )
}

export default LandingPage
