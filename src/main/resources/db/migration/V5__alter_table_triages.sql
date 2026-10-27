ALTER TABLE questions_triages
    ADD COLUMN question_type VARCHAR(20) NOT NULL DEFAULT 'single_choice'
        CHECK (question_type IN ('single_choice', 'multi_choice', 'free_text')),
    ADD COLUMN options       JSONB,
    ADD COLUMN display_order SMALLINT    NOT NULL DEFAULT 0;

INSERT INTO questions_triages (question_text, question_type, options, display_order)
VALUES ('Qual é a sua faixa etária?', 'single_choice', '[
  {
    "code": "AGE_UNDER_18",
    "label": "Menos de 18 anos"
  },
  {
    "code": "AGE_18_24",
    "label": "18 a 24 anos"
  },
  {
    "code": "AGE_25_39",
    "label": "25 a 39 anos"
  },
  {
    "code": "AGE_40_59",
    "label": "40 a 59 anos"
  },
  {
    "code": "AGE_60_PLUS",
    "label": "60 anos ou mais"
  }
]', 1),

       ('O que te trouxe até aqui hoje?', 'multi_choice', '[
         {
           "code": "REDUCE_OR_STOP",
           "label": "Quero diminuir ou parar de usar uma substância"
         },
         {
           "code": "CONCERNED_USE",
           "label": "Estou preocupado(a) com a quantidade ou frequência do meu uso"
         },
         {
           "code": "RECENT_RELAPSE",
           "label": "Tive uma recaída recentemente"
         },
         {
           "code": "FEELING_UNWELL",
           "label": "Não estou me sentindo bem, física ou emocionalmente, por causa do uso"
         },
         {
           "code": "LIFE_IMPACT",
           "label": "O uso tem afetado minha vida pessoal, familiar, profissional ou acadêmica"
         },
         {
           "code": "TALK_NO_JUDGMENT",
           "label": "Quero conversar com alguém sem me sentir julgado(a)"
         },
         {
           "code": "OTHER",
           "label": "Outro motivo"
         }
       ]', 2),

       ('Nos últimos 30 dias, com que frequência você usou a substância que mais te preocupa?', 'single_choice', '[
         {
           "code": "NEVER",
           "label": "Nenhuma vez"
         },
         {
           "code": "ONCE_OR_TWICE",
           "label": "Uma ou duas vezes"
         },
         {
           "code": "FEW_TIMES_MONTH",
           "label": "Algumas vezes no mês"
         },
         {
           "code": "FEW_TIMES_WEEK",
           "label": "Algumas vezes por semana"
         },
         {
           "code": "DAILY",
           "label": "Quase todos os dias ou todos os dias"
         }
       ]', 3),

       ('Você sente que, quando quer parar ou diminuir, é difícil controlar essa vontade?', 'single_choice', '[
         {
           "code": "VERY_HARD",
           "label": "Sim, é bem difícil"
         },
         {
           "code": "SOMETIMES",
           "label": "Às vezes"
         },
         {
           "code": "CAN_CONTROL",
           "label": "Não, consigo controlar quando quero"
         },
         {
           "code": "PREFER_NOT_TO_ANSWER",
           "label": "Prefiro não responder agora"
         }
       ]', 4),

       ('Existe alguém — família, amigos ou algum profissional — com quem você possa contar nesse momento?',
        'single_choice', '[
         {
           "code": "YES_SUPPORTIVE",
           "label": "Sim, e sinto que me apoiam"
         },
         {
           "code": "UNSURE",
           "label": "Tenho alguém, mas não sei se me apoiaria nisso"
         },
         {
           "code": "NO_ONE",
           "label": "No momento não tenho com quem contar"
         },
         {
           "code": "PREFER_NOT_TO_SAY",
           "label": "Prefiro não dizer"
         }
       ]', 5),

       ('Você já procurou algum tipo de tratamento ou apoio antes?', 'single_choice', '[
         {
           "code": "NEVER",
           "label": "Nunca"
         },
         {
           "code": "SUPPORT_GROUP",
           "label": "Sim, grupo de apoio"
         },
         {
           "code": "PSYCHOLOGIST",
           "label": "Sim, psicólogo(a) ou outro profissional"
         },
         {
           "code": "CAPS",
           "label": "Sim, CAPS ou CAPS AD"
         },
         {
           "code": "INPATIENT",
           "label": "Sim, internação ou clínica especializada"
         },
         {
           "code": "OTHER",
           "label": "Outro"
         }
       ]', 6),

       ('Você já teve alguma experiência com atendimento em grupo (roda de conversa, grupo terapêutico etc.)?',
        'single_choice', '[
         {
           "code": "GOOD",
           "label": "Sim, foi uma boa experiência"
         },
         {
           "code": "UNCOMFORTABLE",
           "label": "Sim, mas não me senti à vontade"
         },
         {
           "code": "NEVER",
           "label": "Não, nunca participei"
         },
         {
           "code": "DONT_KNOW",
           "label": "Não sei bem o que é"
         }
       ]', 7),

       ('Pensando em começar agora, qual formato te deixaria mais confortável?', 'single_choice', '[
         {
           "code": "INDIVIDUAL",
           "label": "Atendimento individual"
         },
         {
           "code": "GROUP",
           "label": "Atendimento em grupo"
         },
         {
           "code": "BOTH",
           "label": "Tenho interesse nos dois"
         },
         {
           "code": "UNSURE",
           "label": "Ainda não sei — gostaria de uma indicação profissional"
         }
       ]', 8),

       ('Qual desses estilos de acompanhamento parece mais com o que você procura?', 'single_choice', '[
         {
           "code": "FREE_FLOW",
           "label": "Conversas mais livres, no meu ritmo"
         },
         {
           "code": "STRUCTURED",
           "label": "Encontros mais estruturados, com atividades e metas definidas"
         },
         {
           "code": "TEAM_DECIDES",
           "label": "Não sei — prefiro que a equipe me oriente"
         }
       ]', 9),

       ('Tem algo que você gostaria que a equipe soubesse antes de começar, para você se sentir mais respeitado(a) e à vontade?',
        'free_text', NULL, 10);