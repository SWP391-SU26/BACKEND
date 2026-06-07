import {
  Camera,
  Check,
  LogOut,
  Save,
  ShieldCheck,
  Trash2,
  User,
} from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import Button from '../components/common/Button.jsx'
import { clearSession, getSavedUser, logout as logoutRequest } from '../services/authService.js'

const tabs = [
  { id: 'profile', label: 'Profile' },
  { id: 'account', label: 'Account' },
  { id: 'chat', label: 'Chat Settings' },
  { id: 'security', label: 'Security' },
]

function SettingsPage() {
  const navigate = useNavigate()
  const savedUser = getSavedUser()
  const [activeTab, setActiveTab] = useState('profile')
  const [profile, setProfile] = useState({
    className: 'SE18D01',
    department: 'Software Engineering',
    email: savedUser?.email ?? 'user@fpt.edu.vn',
    fullName: savedUser?.name ?? 'FPT User',
    role: savedUser?.role === 'admin' ? 'Admin' : 'User',
    userCode: 'USR001',
  })
  const [chatSettings, setChatSettings] = useState({
    answerLanguage: 'Vietnamese',
    answerMode: 'Detailed',
    detailedCitations: true,
    saveHistory: true,
  })
  const [savedMessage, setSavedMessage] = useState('')

  function handleProfileChange(event) {
    const { name, value } = event.target
    setProfile((currentProfile) => ({ ...currentProfile, [name]: value }))
    setSavedMessage('')
  }

  function handleChatSettingChange(event) {
    const { checked, name, type, value } = event.target
    setChatSettings((currentSettings) => ({
      ...currentSettings,
      [name]: type === 'checkbox' ? checked : value,
    }))
    setSavedMessage('')
  }

  function handleSave(event) {
    event.preventDefault()
    setSavedMessage('Changes saved successfully.')
  }

  async function handleLogout() {
    try {
      if (savedUser?.id) {
        await logoutRequest(savedUser.id)
      }
    } finally {
      clearSession()
      navigate('/login', { replace: true })
    }
  }

  return (
    <main className="min-h-screen bg-[radial-gradient(circle_at_12%_8%,hsl(var(--primary)/0.12),transparent_30%),linear-gradient(135deg,#ffffff_0%,#f6f8fb_56%,#eef7f5_100%)] p-6 font-body text-foreground">
      <div className="mx-auto max-w-7xl">
        <header className="flex items-center justify-between rounded-[2rem] border border-white/80 bg-white/85 px-6 py-4 shadow-[0_20px_70px_rgba(15,23,42,0.08)]">
          <Link className="inline-flex items-center" to="/app">
            <img
              alt="FStu"
              className="h-10 w-auto object-contain"
              src="/Gemini_Generated_Image_gyb1mfgyb1mfgyb1.png"
            />
          </Link>
          <div className="flex items-center gap-3">
            <Button as={Link} className="rounded-full" to="/app" variant="secondary">
              Back to workspace
            </Button>
            <Button className="rounded-full" onClick={handleLogout} type="button" variant="ghost">
              <LogOut className="size-4" strokeWidth={2} />
              Log out
            </Button>
          </div>
        </header>

        <section className="mt-6 grid min-h-[calc(100vh-8.5rem)] grid-cols-[280px_minmax(0,1fr)] gap-5">
          <aside className="rounded-[2rem] border border-white/80 bg-white/85 p-4 shadow-[0_24px_90px_rgba(15,23,42,0.08)]">
            <div className="rounded-[1.5rem] bg-primary p-5 text-primary-foreground">
              <div className="grid size-14 place-items-center rounded-full bg-white/15">
                <User className="size-7" strokeWidth={2} />
              </div>
              <h1 className="mt-5 text-2xl font-black tracking-tight">
                Settings
              </h1>
              <p className="mt-2 text-sm font-medium leading-6 text-white/75">
                Manage your profile, account, chat preferences, and security.
              </p>
            </div>

            <nav className="mt-4 grid gap-2">
              {tabs.map((tab) => (
                <button
                  className={[
                    'rounded-2xl px-4 py-3 text-left text-sm font-black transition duration-300',
                    activeTab === tab.id
                      ? 'bg-teal-50 text-primary'
                      : 'text-muted-foreground hover:bg-secondary hover:text-foreground',
                  ].join(' ')}
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id)}
                  type="button"
                >
                  {tab.label}
                </button>
              ))}
            </nav>
          </aside>

          <section className="rounded-[2rem] border border-white/80 bg-white/90 p-6 shadow-[0_24px_90px_rgba(15,23,42,0.08)]">
            {savedMessage ? (
              <div className="mb-5 flex items-center gap-3 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-bold text-emerald-700">
                <Check className="size-4" strokeWidth={2} />
                {savedMessage}
              </div>
            ) : null}

            {activeTab === 'profile' ? (
              <ProfileTab
                onChange={handleProfileChange}
                onSave={handleSave}
                profile={profile}
              />
            ) : null}

            {activeTab === 'account' ? (
              <AccountTab onLogout={handleLogout} onSave={handleSave} />
            ) : null}

            {activeTab === 'chat' ? (
              <ChatSettingsTab
                onChange={handleChatSettingChange}
                onSave={handleSave}
                settings={chatSettings}
              />
            ) : null}

            {activeTab === 'security' ? <SecurityTab /> : null}
          </section>
        </section>
      </div>
    </main>
  )
}

function SectionHeader({ description, eyebrow, title }) {
  return (
    <div className="border-b border-border pb-5">
      <p className="text-[10px] font-black uppercase tracking-[0.2em] text-primary">
        {eyebrow}
      </p>
      <h2 className="mt-2 text-3xl font-black tracking-tight">{title}</h2>
      <p className="mt-3 max-w-2xl text-sm leading-6 text-muted-foreground">
        {description}
      </p>
    </div>
  )
}

function Field({ label, name, onChange, placeholder, readOnly, value }) {
  return (
    <label className="block">
      <span className="text-sm font-bold text-foreground">{label}</span>
      <input
        className={[
          'mt-2 h-13 w-full rounded-2xl border border-border px-4 text-sm font-medium outline-none transition duration-300',
          readOnly
            ? 'bg-slate-50 text-muted-foreground'
            : 'bg-secondary focus:border-primary focus:bg-background focus:shadow-[0_0_0_4px_hsl(var(--primary)/0.10)]',
        ].join(' ')}
        name={name}
        onChange={onChange}
        placeholder={placeholder}
        readOnly={readOnly}
        value={value}
      />
    </label>
  )
}

function ProfileTab({ onChange, onSave, profile }) {
  return (
    <form onSubmit={onSave}>
      <SectionHeader
        description="Update personal information used across your FStu workspace."
        eyebrow="Profile"
        title="Personal information"
      />

      <div className="mt-6 flex items-center gap-5 rounded-[1.5rem] border border-border bg-secondary p-5">
        <div className="grid size-20 place-items-center rounded-full bg-primary text-2xl font-black text-primary-foreground">
          FS
        </div>
        <div>
          <h3 className="text-lg font-black">Profile avatar</h3>
          <p className="mt-1 text-sm text-muted-foreground">
            Upload or replace your avatar for the workspace.
          </p>
          <Button className="mt-3 rounded-full" type="button" variant="secondary">
            <Camera className="size-4" strokeWidth={2} />
            Change avatar
          </Button>
        </div>
      </div>

      <div className="mt-6 grid grid-cols-2 gap-5">
        <Field
          label="Full name"
          name="fullName"
          onChange={onChange}
          placeholder="Your name"
          value={profile.fullName}
        />
        <Field label="Email" readOnly value={profile.email} />
        <Field
          label="User code"
          name="userCode"
          onChange={onChange}
          placeholder="USR001"
          value={profile.userCode}
        />
        <Field
          label="Class"
          name="className"
          onChange={onChange}
          placeholder="SE18D01"
          value={profile.className}
        />
        <Field
          label="Department"
          name="department"
          onChange={onChange}
          placeholder="Software Engineering"
          value={profile.department}
        />
        <Field label="Role" readOnly value={profile.role} />
      </div>

      <SaveBar />
    </form>
  )
}

function AccountTab({ onLogout, onSave }) {
  return (
    <form onSubmit={onSave}>
      <SectionHeader
        description="Change your password or leave the current session."
        eyebrow="Account"
        title="Account access"
      />

      <div className="mt-6 grid grid-cols-2 gap-5">
        <Field label="Current password" placeholder="Enter current password" />
        <Field label="New password" placeholder="Enter new password" />
      </div>

      <div className="mt-6 rounded-[1.5rem] border border-border bg-secondary p-5">
        <h3 className="text-lg font-black">Session actions</h3>
        <p className="mt-2 text-sm leading-6 text-muted-foreground">
          Log out clears the local mock token and returns you to the login page.
        </p>
        <div className="mt-4 flex gap-3">
          <Button className="rounded-full" onClick={onLogout} type="button" variant="secondary">
            <LogOut className="size-4" strokeWidth={2} />
            Log out
          </Button>
          <Button className="rounded-full border-red-200 text-red-600 hover:bg-red-50" type="button" variant="secondary">
            <Trash2 className="size-4" strokeWidth={2} />
            Delete account
          </Button>
        </div>
      </div>

      <SaveBar />
    </form>
  )
}

function ChatSettingsTab({ onChange, onSave, settings }) {
  return (
    <form onSubmit={onSave}>
      <SectionHeader
        description="Control how FStu answers and how much context it shows."
        eyebrow="Chat settings"
        title="Answer preferences"
      />

      <div className="mt-6 grid grid-cols-2 gap-5">
        <SelectField
          label="Answer language"
          name="answerLanguage"
          onChange={onChange}
          options={['Vietnamese', 'English']}
          value={settings.answerLanguage}
        />
        <SelectField
          label="Answer mode"
          name="answerMode"
          onChange={onChange}
          options={['Concise', 'Detailed', 'Academic']}
          value={settings.answerMode}
        />
      </div>

      <div className="mt-6 grid gap-3">
        <ToggleRow
          checked={settings.detailedCitations}
          description="Show file, page, chapter, relevance, and chunk preview."
          label="Detailed citations"
          name="detailedCitations"
          onChange={onChange}
        />
        <ToggleRow
          checked={settings.saveHistory}
          description="Keep chat sessions so you can reopen previous study threads."
          label="Save chat history"
          name="saveHistory"
          onChange={onChange}
        />
      </div>

      <SaveBar />
    </form>
  )
}

function SecurityTab() {
  return (
    <div>
      <SectionHeader
        description="Security settings are intentionally lightweight for the current demo."
        eyebrow="Security"
        title="Security overview"
      />

      <div className="mt-6 rounded-[1.5rem] border border-teal-100 bg-teal-50 p-5">
        <ShieldCheck className="size-8 text-primary" strokeWidth={2} />
        <h3 className="mt-4 text-lg font-black">Session protection</h3>
        <p className="mt-2 max-w-2xl text-sm leading-6 text-teal-950/70">
          FStu currently stores a mock access token in local storage for demo
          routing. Real token refresh, MFA, and audit logs should be connected
          when backend authentication is ready.
        </p>
      </div>
    </div>
  )
}

function SelectField({ label, name, onChange, options, value }) {
  return (
    <label className="block">
      <span className="text-sm font-bold text-foreground">{label}</span>
      <select
        className="mt-2 h-13 w-full rounded-2xl border border-border bg-secondary px-4 text-sm font-medium outline-none transition duration-300 focus:border-primary focus:bg-background focus:shadow-[0_0_0_4px_hsl(var(--primary)/0.10)]"
        name={name}
        onChange={onChange}
        value={value}
      >
        {options.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    </label>
  )
}

function ToggleRow({ checked, description, label, name, onChange }) {
  return (
    <label className="flex items-center justify-between gap-5 rounded-[1.5rem] border border-border bg-secondary p-5">
      <span>
        <span className="block text-sm font-black text-foreground">
          {label}
        </span>
        <span className="mt-1 block text-sm leading-6 text-muted-foreground">
          {description}
        </span>
      </span>
      <input
        checked={checked}
        className="size-5 accent-primary"
        name={name}
        onChange={onChange}
        type="checkbox"
      />
    </label>
  )
}

function SaveBar() {
  return (
    <div className="mt-8 flex justify-end border-t border-border pt-5">
      <Button className="rounded-full" type="submit" variant="cta">
        <Save className="size-4" strokeWidth={2} />
        Save changes
      </Button>
    </div>
  )
}

export default SettingsPage
