export type Json =
  | string
  | number
  | boolean
  | null
  | { [key: string]: Json | undefined }
  | Json[]

export type Database = {
  // Allows to automatically instantiate createClient with right options
  // instead of createClient<Database, { PostgrestVersion: 'XX' }>(URL, KEY)
  __InternalSupabase: {
    PostgrestVersion: "14.5"
  }
  public: {
    Tables: {
      admin_settings: {
        Row: {
          key: string
          updated_at: string
          value: string
        }
        Insert: {
          key: string
          updated_at?: string
          value: string
        }
        Update: {
          key?: string
          updated_at?: string
          value?: string
        }
        Relationships: []
      }
      coin_transactions: {
        Row: {
          amount: number
          created_at: string
          id: string
          metadata: Json | null
          reason: string
          user_id: string
        }
        Insert: {
          amount: number
          created_at?: string
          id?: string
          metadata?: Json | null
          reason: string
          user_id: string
        }
        Update: {
          amount?: number
          created_at?: string
          id?: string
          metadata?: Json | null
          reason?: string
          user_id?: string
        }
        Relationships: []
      }
      external_references: {
        Row: {
          amount: number
          created_at: string
          credited: boolean
          credited_at: string | null
          email: string | null
          external_ref: string
          id: string
          operator: string | null
          payid: string | null
          phone: string | null
          status: string
          user_id: string
        }
        Insert: {
          amount: number
          created_at?: string
          credited?: boolean
          credited_at?: string | null
          email?: string | null
          external_ref: string
          id?: string
          operator?: string | null
          payid?: string | null
          phone?: string | null
          status?: string
          user_id: string
        }
        Update: {
          amount?: number
          created_at?: string
          credited?: boolean
          credited_at?: string | null
          email?: string | null
          external_ref?: string
          id?: string
          operator?: string | null
          payid?: string | null
          phone?: string | null
          status?: string
          user_id?: string
        }
        Relationships: []
      }
      playlist_links: {
        Row: {
          created_at: string
          id: string
          is_active: boolean
          url: string
        }
        Insert: {
          created_at?: string
          id?: string
          is_active?: boolean
          url: string
        }
        Update: {
          created_at?: string
          id?: string
          is_active?: boolean
          url?: string
        }
        Relationships: []
      }
      profiles: {
        Row: {
          blocked_reason: string | null
          coins: number
          created_at: string
          display_name: string | null
          id: string
          is_blocked: boolean
          session_token: string | null
          session_updated_at: string | null
          updated_at: string
          user_id: string
        }
        Insert: {
          blocked_reason?: string | null
          coins?: number
          created_at?: string
          display_name?: string | null
          id?: string
          is_blocked?: boolean
          session_token?: string | null
          session_updated_at?: string | null
          updated_at?: string
          user_id: string
        }
        Update: {
          blocked_reason?: string | null
          coins?: number
          created_at?: string
          display_name?: string | null
          id?: string
          is_blocked?: boolean
          session_token?: string | null
          session_updated_at?: string | null
          updated_at?: string
          user_id?: string
        }
        Relationships: []
      }
      subscription_plans: {
        Row: {
          created_at: string
          duration_minutes: number
          id: number
          is_popular: boolean
          name: string
          price_coins: number
          sort_order: number
        }
        Insert: {
          created_at?: string
          duration_minutes: number
          id?: number
          is_popular?: boolean
          name: string
          price_coins: number
          sort_order?: number
        }
        Update: {
          created_at?: string
          duration_minutes?: number
          id?: number
          is_popular?: boolean
          name?: string
          price_coins?: number
          sort_order?: number
        }
        Relationships: []
      }
      subscriptions: {
        Row: {
          created_at: string
          expires_at: string
          id: string
          plan_id: number
          playlist_link_id: string | null
          starts_at: string
          user_id: string
        }
        Insert: {
          created_at?: string
          expires_at: string
          id?: string
          plan_id: number
          playlist_link_id?: string | null
          starts_at?: string
          user_id: string
        }
        Update: {
          created_at?: string
          expires_at?: string
          id?: string
          plan_id?: number
          playlist_link_id?: string | null
          starts_at?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "subscriptions_plan_id_fkey"
            columns: ["plan_id"]
            isOneToOne: false
            referencedRelation: "subscription_plans"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "subscriptions_playlist_link_id_fkey"
            columns: ["playlist_link_id"]
            isOneToOne: false
            referencedRelation: "playlist_links"
            referencedColumns: ["id"]
          },
        ]
      }
      user_roles: {
        Row: {
          created_at: string
          id: string
          role: Database["public"]["Enums"]["app_role"]
          user_id: string
        }
        Insert: {
          created_at?: string
          id?: string
          role: Database["public"]["Enums"]["app_role"]
          user_id: string
        }
        Update: {
          created_at?: string
          id?: string
          role?: Database["public"]["Enums"]["app_role"]
          user_id?: string
        }
        Relationships: []
      }
      webhook_events: {
        Row: {
          created_at: string
          external_ref: string | null
          id: string
          message: string | null
          payload: Json
          processed: boolean
          source: string
        }
        Insert: {
          created_at?: string
          external_ref?: string | null
          id?: string
          message?: string | null
          payload: Json
          processed?: boolean
          source?: string
        }
        Update: {
          created_at?: string
          external_ref?: string | null
          id?: string
          message?: string | null
          payload?: Json
          processed?: boolean
          source?: string
        }
        Relationships: []
      }
    }
    Views: {
      [_ in never]: never
    }
    Functions: {
      admin_add_playlist_link: { Args: { p_url: string }; Returns: string }
      admin_adjust_coins: {
        Args: { p_delta: number; p_reason: string; p_user_id: string }
        Returns: number
      }
      admin_block_user: {
        Args: { p_reason: string; p_user_id: string }
        Returns: undefined
      }
      admin_delete_plan: { Args: { p_id: number }; Returns: undefined }
      admin_delete_playlist_link: { Args: { p_id: string }; Returns: undefined }
      admin_delete_user: { Args: { p_user_id: string }; Returns: undefined }
      admin_get_setting: { Args: { p_key: string }; Returns: string }
      admin_list_plans: {
        Args: never
        Returns: {
          created_at: string
          duration_minutes: number
          id: number
          is_popular: boolean
          name: string
          price_coins: number
          sort_order: number
        }[]
        SetofOptions: {
          from: "*"
          to: "subscription_plans"
          isOneToOne: false
          isSetofReturn: true
        }
      }
      admin_list_playlist_links: {
        Args: never
        Returns: {
          assigned_email: string
          created_at: string
          expires_at: string
          id: string
          in_use: boolean
          is_active: boolean
          url: string
        }[]
      }
      admin_list_subscriptions: {
        Args: { p_limit?: number }
        Returns: {
          created_at: string
          email: string
          expires_at: string
          id: string
          is_active: boolean
          plan_id: number
          plan_name: string
          starts_at: string
          user_id: string
        }[]
      }
      admin_list_transactions: {
        Args: { p_limit?: number }
        Returns: {
          amount: number
          created_at: string
          email: string
          id: string
          metadata: Json
          reason: string
          user_id: string
        }[]
      }
      admin_list_users: {
        Args: never
        Returns: {
          blocked_reason: string
          coins: number
          created_at: string
          display_name: string
          email: string
          has_active_session: boolean
          is_blocked: boolean
          last_sign_in: string
          user_id: string
        }[]
      }
      admin_set_setting: {
        Args: { p_key: string; p_value: string }
        Returns: undefined
      }
      admin_unblock_user: { Args: { p_user_id: string }; Returns: undefined }
      admin_upsert_plan: {
        Args: {
          p_duration_minutes: number
          p_id: number
          p_name: string
          p_price_coins: number
          p_sort_order: number
        }
        Returns: number
      }
      check_session: {
        Args: { p_token: string }
        Returns: {
          blocked_reason: string
          status: string
        }[]
      }
      claim_session: { Args: { p_token: string }; Returns: undefined }
      get_active_subscription: {
        Args: never
        Returns: {
          expires_at: string
          id: string
          plan_id: number
          plan_name: string
          seconds_remaining: number
          starts_at: string
        }[]
      }
      get_my_playlist_url: { Args: never; Returns: string }
      has_role: {
        Args: {
          _role: Database["public"]["Enums"]["app_role"]
          _user_id: string
        }
        Returns: boolean
      }
      process_soleaspay_webhook: { Args: { p_payload: Json }; Returns: Json }
      purchase_coins:
        | {
            Args: { p_amount: number; p_operator: string; p_phone: string }
            Returns: {
              coins: number
            }[]
          }
        | {
            Args: {
              p_amount: number
              p_operator: string
              p_payid?: string
              p_phone: string
              p_status?: string
            }
            Returns: {
              coins: number
            }[]
          }
      purchase_plan: {
        Args: { p_plan_id: number }
        Returns: {
          coins: number
          expires_at: string
        }[]
      }
      register_external_reference: {
        Args: {
          p_amount: number
          p_external_ref: string
          p_operator: string
          p_phone: string
        }
        Returns: undefined
      }
      register_external_reference_public: {
        Args: {
          p_amount: number
          p_email: string
          p_external_ref: string
          p_operator: string
          p_phone: string
        }
        Returns: string
      }
    }
    Enums: {
      app_role: "admin" | "user"
    }
    CompositeTypes: {
      [_ in never]: never
    }
  }
}

type DatabaseWithoutInternals = Omit<Database, "__InternalSupabase">

type DefaultSchema = DatabaseWithoutInternals[Extract<keyof Database, "public">]

export type Tables<
  DefaultSchemaTableNameOrOptions extends
    | keyof (DefaultSchema["Tables"] & DefaultSchema["Views"])
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof (DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"] &
        DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Views"])
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? (DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"] &
      DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Views"])[TableName] extends {
      Row: infer R
    }
    ? R
    : never
  : DefaultSchemaTableNameOrOptions extends keyof (DefaultSchema["Tables"] &
        DefaultSchema["Views"])
    ? (DefaultSchema["Tables"] &
        DefaultSchema["Views"])[DefaultSchemaTableNameOrOptions] extends {
        Row: infer R
      }
      ? R
      : never
    : never

export type TablesInsert<
  DefaultSchemaTableNameOrOptions extends
    | keyof DefaultSchema["Tables"]
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"]
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"][TableName] extends {
      Insert: infer I
    }
    ? I
    : never
  : DefaultSchemaTableNameOrOptions extends keyof DefaultSchema["Tables"]
    ? DefaultSchema["Tables"][DefaultSchemaTableNameOrOptions] extends {
        Insert: infer I
      }
      ? I
      : never
    : never

export type TablesUpdate<
  DefaultSchemaTableNameOrOptions extends
    | keyof DefaultSchema["Tables"]
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"]
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"][TableName] extends {
      Update: infer U
    }
    ? U
    : never
  : DefaultSchemaTableNameOrOptions extends keyof DefaultSchema["Tables"]
    ? DefaultSchema["Tables"][DefaultSchemaTableNameOrOptions] extends {
        Update: infer U
      }
      ? U
      : never
    : never

export type Enums<
  DefaultSchemaEnumNameOrOptions extends
    | keyof DefaultSchema["Enums"]
    | { schema: keyof DatabaseWithoutInternals },
  EnumName extends DefaultSchemaEnumNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaEnumNameOrOptions["schema"]]["Enums"]
    : never = never,
> = DefaultSchemaEnumNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaEnumNameOrOptions["schema"]]["Enums"][EnumName]
  : DefaultSchemaEnumNameOrOptions extends keyof DefaultSchema["Enums"]
    ? DefaultSchema["Enums"][DefaultSchemaEnumNameOrOptions]
    : never

export type CompositeTypes<
  PublicCompositeTypeNameOrOptions extends
    | keyof DefaultSchema["CompositeTypes"]
    | { schema: keyof DatabaseWithoutInternals },
  CompositeTypeName extends PublicCompositeTypeNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[PublicCompositeTypeNameOrOptions["schema"]]["CompositeTypes"]
    : never = never,
> = PublicCompositeTypeNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[PublicCompositeTypeNameOrOptions["schema"]]["CompositeTypes"][CompositeTypeName]
  : PublicCompositeTypeNameOrOptions extends keyof DefaultSchema["CompositeTypes"]
    ? DefaultSchema["CompositeTypes"][PublicCompositeTypeNameOrOptions]
    : never

export const Constants = {
  public: {
    Enums: {
      app_role: ["admin", "user"],
    },
  },
} as const
