import { ArrowLeft } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Button, EmptyState } from '../components/ui.jsx'
import { studyImage } from '../data/mockData.js'

function NotFoundPage() {
  return (
    <EmptyState
      action={
        <Link to="/workspace">
          <Button>
            <ArrowLeft size={16} />
            Back to workspace
          </Button>
        </Link>
      }
      description="This frontend route does not exist. Use AI Chat or Library to return to the main FStu workspace."
      image={studyImage}
      title="Page not found"
    />
  )
}

export default NotFoundPage
