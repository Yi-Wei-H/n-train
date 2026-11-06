import {
  Calendar,
  ChatDotRound,
  Cpu,
  DataLine,
  Document,
  Files,
  Grid,
  MagicStick,
  Menu,
  Postcard,
  Setting,
  Share,
  Suitcase,
  Tickets,
  Timer,
  User,
  UserFilled,
  Van
} from '@element-plus/icons-vue'

const ICONS = {
  setting: Setting,
  train: Van,
  date: Calendar,
  form: Tickets,
  people: User,
  'tree-table': Grid,
  tree: Share,
  log: Document,
  time: Timer,
  seat: Suitcase,
  ticket: Tickets,
  ai: MagicStick,
  model: Cpu,
  preset: Files,
  chat: ChatDotRound,
  card: Postcard,
  chart: DataLine,
  user: User,
  peoples: UserFilled
}

export function menuIcon(name) {
  return ICONS[name] || Menu
}
