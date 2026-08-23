package pladBack.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "result_questions_triages")
public class ResultQuestionTriage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "triage_id", nullable = false)
    private Triage triage;

    @ManyToOne
    @JoinColumn(name = "question_id", nullable = false)
    private QuestionTriage question;

    @Column(name = "value_result")
    private String valueResult;

    //getters n setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Triage getTriage() {
        return triage;
    }

    public void setTriage(Triage triage) {
        this.triage = triage;
    }

    public QuestionTriage getQuestion() {
        return question;
    }

    public void setQuestion(QuestionTriage question) {
        this.question = question;
    }

    public String getValueResult() {
        return valueResult;
    }

    public void setValueResult(String valueResult) {
        this.valueResult = valueResult;
    }

    //empty constructor
    public ResultQuestionTriage() {}

}
