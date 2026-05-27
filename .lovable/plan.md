# Refonte FLOW+ — Style Netflix avec abonnements

## Vue d'ensemble

Transformer l'app web actuelle en une expérience streaming type Netflix, avec authentification, système d'abonnements temporisés payés en "pièces", page d'achat de pièces (Mobile Money intégré plus tard), et nouveau branding **FLOW+**.

## 1. Branding & Identité

- Logo **FLOW+** : copier l'image uploadée dans `src/assets/flow-logo.png`, remplacer le "TV" doré de l'image par **"LIVE"** doré dans une variante générée
- Nom de l'app : **FLOW+** partout (titre, meta, manifest, splash)
- Icône PWA / splash style Netflix : fond noir, logo centré, animation fade-in
- Palette : noir profond `#000`, rouge Netflix `#E50914`, or `#D4AF37` pour accents premium
- Typo : Inter / sans bold pour titres, semblable Netflix Sans

## 2. Backend — Lovable Cloud

### Auth
- Email + mot de passe, **auto-confirm activé** (pas de vérification email)
- Pas de Google OAuth (l'utilisateur n'a pas demandé)

### Nouvelles tables

- **profiles** : `id`, `user_id` (FK auth.users), `display_name`, `coins` (int, défaut 120), `created_at`
  - Trigger `handle_new_user` crée le profil et crédite **120 FCFA** (= 120 pièces) à l'inscription
- **subscription_plans** : `id`, `name`, `duration_minutes`, `price_coins`, `sort_order`
  - Seed : 1h30 / 100, 3h30 / 200, 6h30 / 300, 24h / 600, 7j / 1000, 14j / 1500
- **subscriptions** : `id`, `user_id`, `plan_id`, `starts_at`, `expires_at`, `created_at`
  - L'abonnement actif = `expires_at > now()`
- **coin_transactions** : `id`, `user_id`, `amount` (+/-), `reason` (`signup_bonus` | `purchase` | `plan_purchase`), `created_at`

### RLS
- profiles : user voit/édite le sien
- subscription_plans : lecture publique
- subscriptions, coin_transactions : user voit les siennes, insertion via server functions uniquement

### Server functions
- `purchaseCoins(amount)` — stub pour Mobile Money (crée juste une transaction +coins pour test)
- `purchasePlan(planId)` — déduit les pièces, crée la subscription
- `getActiveSubscription()` — renvoie l'abonnement actif + temps restant
- `getProfile()` — renvoie profil avec solde de pièces

## 3. Pages & Navigation

```text
/              → Landing (logo, hero, CTA Se connecter / S'inscrire)
/login         → Connexion
/signup        → Inscription (+ bonus 120 FCFA affiché)
/_authenticated/
  /browse      → Accueil Netflix (lignes par catégorie de chaînes)
  /channel/$id → Page description chaîne + bouton Lecture (gated par sub active)
  /plans       → Liste des 6 plans, achat avec pièces
  /coins       → Acheter des pièces (Mobile Money — UI prête, paiement stub)
  /account     → Profil, solde, abonnement actif, historique
```

Garde sous `_authenticated/` : redirige `/login` si non connecté.

Gate de lecture : `/channel/$id` → si pas de sub active, redirige vers `/plans` avec un message.

## 4. Design Netflix-style

- **Header** : transparent → noir au scroll, logo FLOW+ à gauche, nav (Accueil, Plans, Pièces, Compte), avatar à droite
- **Hero** : grande chaîne en vedette, gradient noir en bas, boutons "▶ Lecture" + "ⓘ Plus d'infos"
- **Rangées horizontales** : une par `group_title` (Sport, Films, Séries, Actualités, etc.), scroll horizontal, vignettes avec logo de chaîne
- **Carte chaîne** : logo sur fond gris, hover scale + bordure rouge
- **Page chaîne** : backdrop flou du logo, titre, catégorie, description, gros bouton Lecture rouge
- **Player** : plein écran, contrôles overlay, badge temps restant abonnement

## 5. Classement des chaînes

Réutiliser le `group_title` du M3U déjà parsé. Trier les groupes par nombre de chaînes (déjà fait par `getGroups`). Sur `/browse`, afficher une rangée par groupe, top 20 chaînes/groupe en preview, "Voir tout" pour la liste complète du groupe.

## 6. Mobile Money (préparation)

Page `/coins` avec :
- Packs : 500, 1000, 2000, 5000 FCFA
- Sélecteur opérateur (Orange Money, MTN MoMo, Moov Money, Wave) — UI seulement
- Champ numéro
- Bouton "Payer" → appelle `purchaseCoins` (stub qui crédite directement en dev)
- Encart "Intégration paiement en cours"

## 7. Détails techniques

### Fichiers à créer
- `src/assets/flow-logo.png` (copie de l'upload)
- `src/components/layout/Header.tsx`, `Footer.tsx`
- `src/components/iptv/ChannelRow.tsx`, `ChannelCard.tsx`, `HeroChannel.tsx`
- `src/components/subscription/PlanCard.tsx`, `CoinPack.tsx`, `SubscriptionBadge.tsx`
- `src/hooks/useAuth.ts`, `useProfile.ts`, `useActiveSubscription.ts`
- `src/lib/subscriptions.functions.ts`, `coins.functions.ts`, `profile.functions.ts`
- Routes : `login.tsx`, `signup.tsx`, `_authenticated.tsx`, `_authenticated/browse.tsx`, `_authenticated/channel.$id.tsx`, `_authenticated/plans.tsx`, `_authenticated/coins.tsx`, `_authenticated/account.tsx`
- Refonte `src/routes/index.tsx` en landing
- `src/styles.css` : tokens Netflix (rouge, noir, or, gradients, ombres)

### Migration DB
Une seule migration avec : enum, 4 tables, RLS, trigger `handle_new_user`, seed des 6 plans.

### Config
- `supabase--configure_auth` : `auto_confirm_email: true`
- App Android : mise à jour ultérieure (hors scope de ce tour, l'app web d'abord)

## 8. Hors scope (à confirmer)

- Intégration réelle Mobile Money (sera fait après comme demandé)
- Mise à jour de l'APK Android avec ce nouveau design (web d'abord, APK ensuite)
- EPG / guide programme
- Profils multiples (façon Netflix)

## Ordre d'exécution

1. Migration DB + seed plans + config auth
2. Server functions (profile, coins, subscriptions)
3. Logo + design tokens Netflix
4. Routes auth (landing, login, signup)
5. Layout authentifié + header/footer
6. `/browse` Netflix-style avec rangées
7. Page chaîne + gate abonnement
8. `/plans` + `/coins` + `/account`
9. QA visuel
