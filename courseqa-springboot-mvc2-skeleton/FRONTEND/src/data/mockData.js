export const studyImage =
  'https://images.pexels.com/photos/7777679/pexels-photo-7777679.jpeg?auto=compress&cs=tinysrgb&w=1400'

export const workspaceImage =
  'https://images.unsplash.com/photo-1519389950473-47ba0277781c?auto=format&fit=crop&w=1400&q=85'

export const libraryImage =
  'https://images.unsplash.com/photo-1524995997946-a1c2e315a42f?auto=format&fit=crop&w=1400&q=85'

export const detailImage =
  'https://images.unsplash.com/photo-1456513080510-7bf3a84b82f8?auto=format&fit=crop&w=1400&q=85'

export const avatarImage =
  'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=180&q=80'

export const workspaces = [
  { id: 'ai', name: 'Artificial Intelligence', term: 'FALL 2026', color: 'teal' },
  { id: 'swe', name: 'Software Project', term: 'FALL 2026', color: 'emerald' },
  { id: 'db', name: 'Database Systems', term: 'FALL 2026', color: 'slate' },
]

export const documents = [
  {
    id: 'doc-ai-01',
    name: 'AI Foundations - Lecture 01.pdf',
    displayName: 'AI Foundations - Lecture 01',
    type: 'PDF',
    subject: 'Artificial Intelligence',
    chapter: 'Chapter 1',
    status: 'Indexed',
    chunks: 48,
    embeddingModel: 'text-embedding-3-small',
    uploadedAt: 'May 26, 2026 21:14',
    size: '8.4 MB',
    pages: 42,
    relevance: 94,
    workspaceId: 'ai',
    preview:
      'An introduction to intelligent agents, environments, knowledge representation, and practical AI applications for study workflows.',
  },
  {
    id: 'doc-ai-02',
    name: 'Search Algorithms.pptx',
    displayName: 'Search Algorithms',
    type: 'PPTX',
    subject: 'Artificial Intelligence',
    chapter: 'Chapter 2',
    status: 'Processing',
    chunks: 0,
    embeddingModel: 'text-embedding-3-small',
    uploadedAt: 'May 27, 2026 09:38',
    size: '14.1 MB',
    pages: 68,
    relevance: 0,
    workspaceId: 'ai',
    preview:
      'Slides covering uninformed search, heuristic search, A*, greedy best-first search, and complexity evaluation.',
  },
  {
    id: 'doc-swe-01',
    name: 'SRS Template.docx',
    displayName: 'SRS Template',
    type: 'DOCX',
    subject: 'Software Project',
    chapter: 'Chapter 3',
    status: 'Indexed',
    chunks: 31,
    embeddingModel: 'text-embedding-3-large',
    uploadedAt: 'May 25, 2026 18:02',
    size: '1.9 MB',
    pages: 18,
    relevance: 87,
    workspaceId: 'swe',
    preview:
      'A software requirements specification template with scope, actors, use cases, constraints, and acceptance criteria.',
  },
  {
    id: 'doc-db-01',
    name: 'Normalization Notes.pdf',
    displayName: 'Normalization Notes',
    type: 'PDF',
    subject: 'Database Systems',
    chapter: 'Chapter 4',
    status: 'Failed',
    chunks: 12,
    embeddingModel: 'text-embedding-3-small',
    uploadedAt: 'May 23, 2026 14:27',
    size: '5.7 MB',
    pages: 26,
    relevance: 0,
    workspaceId: 'db',
    preview:
      'Notes on functional dependencies, candidate keys, 1NF, 2NF, 3NF, and BCNF for relational database design.',
  },
  {
    id: 'doc-ai-03',
    name: 'Knowledge Representation.docx',
    displayName: 'Knowledge Representation',
    type: 'DOCX',
    subject: 'Artificial Intelligence',
    chapter: 'Chapter 5',
    status: 'Uploaded',
    chunks: 0,
    embeddingModel: 'Not embedded',
    uploadedAt: 'May 27, 2026 10:12',
    size: '2.6 MB',
    pages: 21,
    relevance: 0,
    workspaceId: 'ai',
    preview:
      'A queued document covering propositional logic, predicate logic, ontologies, and knowledge-based inference.',
  },
]

export const chunks = [
  {
    id: 'C-1024',
    documentId: 'doc-ai-01',
    page: 6,
    tokenLength: 226,
    metadata: 'agent, environment, rationality',
    relevance: 94,
    content:
      'An intelligent agent receives percepts from its environment through sensors and performs actions through actuators. Rationality is evaluated against a performance measure rather than an isolated action.',
  },
  {
    id: 'C-1031',
    documentId: 'doc-ai-01',
    page: 12,
    tokenLength: 184,
    metadata: 'search, state space',
    relevance: 89,
    content:
      'A search problem is modeled with an initial state, available actions, a transition model, a goal test, and a path cost. A solution is a sequence of actions that reaches the goal state.',
  },
  {
    id: 'C-1188',
    documentId: 'doc-ai-01',
    page: 29,
    tokenLength: 241,
    metadata: 'knowledge base, inference',
    relevance: 82,
    content:
      'A knowledge-based system stores facts and rules in a knowledge base. Its inference engine applies reasoning rules to derive new knowledge or answer queries from the available sources.',
  },
  {
    id: 'C-2110',
    documentId: 'doc-swe-01',
    page: 4,
    tokenLength: 198,
    metadata: 'requirement, use case',
    relevance: 88,
    content:
      'A strong software requirement is testable, identifies the relevant actor, and describes its trigger. A use case should include the main flow, alternate flows, and postconditions.',
  },
  {
    id: 'C-4012',
    documentId: 'doc-db-01',
    page: 9,
    tokenLength: 176,
    metadata: 'normal form, dependency',
    relevance: 71,
    content:
      'Normalization reduces redundant data by decomposing relations based on functional dependencies. A useful decomposition preserves information and limits update anomalies.',
  },
]

export const chatSessions = [
  { id: 'session-1', title: 'Review intelligent agents', updatedAt: '10 minutes ago', workspaceId: 'ai' },
  { id: 'session-2', title: 'Summarize search algorithms', updatedAt: 'This morning', workspaceId: 'ai' },
  { id: 'session-3', title: 'Draft SRS use cases', updatedAt: 'Yesterday', workspaceId: 'swe' },
]

export const initialMessages = [
  {
    id: 'm-1',
    role: 'assistant',
    content:
      'I am ready to answer questions from the indexed documents in this workspace. Ask for a summary, compare concepts, or generate review questions.',
    citations: [],
  },
  {
    id: 'm-2',
    role: 'user',
    content: 'How does the source define an intelligent agent?',
  },
  {
    id: 'm-3',
    role: 'assistant',
    content:
      'An intelligent agent receives information from the environment through sensors and acts through actuators. The important point is that its behavior is evaluated against a performance measure and the context of the environment.',
    citations: ['C-1024', 'C-1188'],
    confidence: 'good',
  },
]

export const uploadSteps = [
  'Uploaded',
  'Extracting text',
  'Chunking',
  'Embedding',
  'Indexed',
]
