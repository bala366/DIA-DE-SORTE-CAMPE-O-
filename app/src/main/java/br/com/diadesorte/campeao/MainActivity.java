package br.com.diadesorte.campeao;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
  final int PICK=100, SAVE=101;
  final String[] months={"Janeiro","Fevereiro","Março","Abril","Maio","Junho","Julho","Agosto","Setembro","Outubro","Novembro","Dezembro"};
  ArrayList<int[]> draws=new ArrayList<>(); ArrayList<Integer> drawnMonths=new ArrayList<>();
  ArrayList<int[]> groups=new ArrayList<>(); ArrayList<Stat> stats=new ArrayList<>();
  HashSet<Integer> excluded=new HashSet<>(); int[] chosen=new int[7]; int month=1;
  TextView status; LinearLayout root; int current=0; boolean ready=false;
  static class Stat { int mask, n, fail, delay, streak, last, size; }
  int[] counts=new int[32];
  @Override public void onCreate(Bundle b){super.onCreate(b);buildUi();}
  TextView text(String s,int size){ TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(Color.BLACK);v.setPadding(12,12,12,12);return v; }
  Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setOnClickListener(v->r.run());root.addView(b);return b;}
  void buildUi(){ScrollView sc=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(1);root.setPadding(18,16,18,20);sc.addView(root);setContentView(sc);
    TextView head=text("☀ DIA DE SORTE CAMPEÃO V6",23);head.setGravity(Gravity.CENTER);head.setTypeface(null,1);head.setBackgroundColor(Color.rgb(255,215,0));root.addView(head);
    root.addView(text("8 tirinhas • 255 combinações • 7 dezenas + Mês da Sorte",14));
    button("1. SELECIONAR HISTÓRICO TXT/CSV",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK);});
    button("2. ANALISAR TODAS AS TIRINHAS",()->analyze());
    button("3. AVALIAR TIRINHAS PARA FALHA",()->nextGroup());
    button("4. GERAR JOGO E MÊS",()->generate());
    button("5. SALVAR VOLANTE PDF",()->savePdf());
    status=text("Selecione o histórico para começar.",15);root.addView(status);
  }
  void say(String s){status.setText(s);}
  @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;
    if(request==PICK){try{read(data.getData());}catch(Exception e){say("ERRO NA LEITURA: "+e.getMessage());}}
    if(request==SAVE){try{writePdf(data.getData());}catch(Exception e){say("ERRO AO SALVAR PDF: "+e.getMessage());}}
  }
  void read(Uri uri)throws Exception{draws.clear();drawnMonths.clear();stats.clear();excluded.clear();ready=false;
    BufferedReader br=new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri),"UTF-8"));String line;int ignored=0;
    Pattern p=Pattern.compile("^\\s*(\\d+)\\s*-\\s*([0-9,;\\s]+)\\s*-\\s*([\\p{L}çãéêíóúâôÁÉÊÍÓÚÃÕ]+)\\s*$");
    TreeMap<Integer,int[]> parsed=new TreeMap<>();HashMap<Integer,Integer> monthMap=new HashMap<>();
    while((line=br.readLine())!=null){Matcher m=p.matcher(line.trim());if(!m.matches()){ignored++;continue;}
      int contest=Integer.parseInt(m.group(1));String[] fields=m.group(2).trim().split("[,;\\s]+");if(fields.length!=7){ignored++;continue;}
      int[] d=new int[7];HashSet<Integer> unique=new HashSet<>();boolean ok=true;
      for(int i=0;i<7;i++){d[i]=Integer.parseInt(fields[i]);if(d[i]<1||d[i]>31||!unique.add(d[i]))ok=false;}
      int mm=monthNumber(m.group(3));if(!ok||mm==0){ignored++;continue;}parsed.put(contest,d);monthMap.put(contest,mm);
    }br.close();if(parsed.size()<10)throw new Exception("Menos de 10 concursos válidos. Linhas ignoradas: "+ignored);
    int first=parsed.firstKey(),last=parsed.lastKey();if(parsed.size()!=last-first+1)throw new Exception("Há concursos faltando entre "+first+" e "+last+". Encontrados: "+parsed.size());
    for(int k:parsed.keySet()){draws.add(parsed.get(k));drawnMonths.add(monthMap.get(k));}
    say("Histórico carregado: "+draws.size()+" concursos ("+first+" a "+last+"). Linhas ignoradas: "+ignored+". Toque em ANALISAR.");
  }
  int monthNumber(String s){for(int i=0;i<12;i++)if(months[i].equalsIgnoreCase(s))return i+1;return 0;}
  void analyze(){if(draws.isEmpty()){say("Selecione o histórico primeiro.");return;}
    Arrays.fill(counts,0);int[] masks=new int[draws.size()];for(int i=0;i<draws.size();i++){int mask=0;for(int n:draws.get(i)){mask|=1<<(n-1);counts[n]++;}masks[i]=mask;}
    stats.clear();for(int subset=1;subset<256;subset++){Stat st=new Stat();st.mask=0;st.n=subset;st.size=0;for(int t=0;t<8;t++)if((subset&(1<<t))!=0){for(int n=t*4+1;n<=Math.min(31,t*4+4);n++){st.mask|=1<<(n-1);st.size++;}}
      int run=0;st.last=-1;for(int i=0;i<masks.length;i++){if((st.mask&masks[i])==0){st.fail++;run++;st.streak=Math.max(st.streak,run);st.last=i;}else run=0;}st.delay=st.last<0?masks.length:masks.length-1-st.last;stats.add(st);
    }
    Collections.sort(stats,(a,b)->{int z=Integer.compare(b.delay,a.delay);if(z!=0)return z;return Integer.compare(b.fail,a.fail);});current=0;ready=true;
    say("Analisadas 255 combinações em "+draws.size()+" concursos. Ranking por atraso de falha inteira. Toque em AVALIAR TIRINHAS.");
  }
  String stripNames(Stat s){StringBuilder x=new StringBuilder();for(int i=0;i<8;i++)if((s.n&(1<<i))!=0)x.append("T").append(i+1).append(" ");return x.toString();}
  String nums(int mask){StringBuilder s=new StringBuilder();for(int n=1;n<=31;n++)if((mask&(1<<(n-1)))!=0)s.append(String.format(java.util.Locale.US,"%02d ",n));return s.toString();}
  void nextGroup(){if(!ready){say("Analise os concursos primeiro.");return;}if(current>=stats.size()){say("Fim das 255 opções.");return;}Stat s=stats.get(current);
    new AlertDialog.Builder(this).setTitle("Opção "+(current+1)+"/255 — "+stripNames(s)).setMessage("Dezenas: "+nums(s.mask)+"\nQuantidade: "+s.size+"\nFalhas históricas: "+s.fail+"\nAtraso atual: "+s.delay+"\nMaior sequência: "+s.streak+"\n\nRetirar estas dezenas do jogo?")
      .setPositiveButton("SIM",(d,w)->{excluded.clear();for(int n=1;n<=31;n++)if((s.mask&(1<<(n-1)))!=0)excluded.add(n);say("Tirinhas aprovadas: "+stripNames(s)+"\nExcluídas: "+nums(s.mask)+"\nAgora toque em GERAR JOGO.");})
      .setNegativeButton("NÃO, PRÓXIMA",(d,w)->{current++;nextGroup();}).setNeutralButton("CANCELAR",null).show();
  }
  void generate(){if(draws.isEmpty()){say("Carregue o histórico.");return;}ArrayList<Integer> pool=new ArrayList<>();for(int n=1;n<=31;n++)if(!excluded.contains(n))pool.add(n);
    if(pool.size()<7){say("Você excluiu dezenas demais. Escolha outras tirinhas.");return;}
    // Critério histórico descritivo: menos frequentes nos últimos 100, depois no histórico completo.
    int[] recent=new int[32];for(int i=Math.max(0,draws.size()-100);i<draws.size();i++)for(int n:draws.get(i))recent[n]++;
    pool.sort((a,b)->{int x=Integer.compare(recent[a],recent[b]);if(x!=0)return x;x=Integer.compare(counts[a],counts[b]);if(x!=0)return x;return Integer.compare(a,b);});
    for(int i=0;i<7;i++)chosen[i]=pool.get(i);Arrays.sort(chosen);
    int[] freq=new int[13];for(int m:drawnMonths)freq[m]++;month=1;for(int m=2;m<=12;m++)if(freq[m]<freq[month])month=m;
    StringBuilder sb=new StringBuilder("JOGO SUGERIDO: ");for(int n:chosen)sb.append(String.format(java.util.Locale.US,"%02d ",n));sb.append("\nMÊS DA SORTE: ").append(months[month-1]);sb.append("\nCritério: dezenas menos frequentes nos últimos 100 concursos; mês menos frequente no histórico.");say(sb.toString());
    new AlertDialog.Builder(this).setTitle("Mês da Sorte").setSingleChoiceItems(months,month-1,(dialog,which)->{month=which+1;dialog.dismiss();say(sb.toString()+"\nMês selecionado: "+months[month-1]);}).setNegativeButton("MANTER",null).show();
  }
  void savePdf(){if(chosen[0]==0){say("Gere o jogo antes de salvar o PDF.");return;}Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/pdf");i.putExtra(Intent.EXTRA_TITLE,"DIA_DE_SORTE_CAMPEAO_V6_VOLANTE.pdf");startActivityForResult(i,SAVE);}
  void writePdf(Uri uri)throws Exception{PdfDocument doc=new PdfDocument();PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(595,842,1).create();PdfDocument.Page page=doc.startPage(info);Canvas c=page.getCanvas();Paint p=new Paint(3);
    p.setColor(Color.rgb(255,215,0));c.drawRect(30,35,565,100,p);p.setColor(Color.BLACK);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));p.setTextSize(23);c.drawText("DIA DE SORTE CAMPEÃO V6",45,77,p);
    p.setTextSize(15);c.drawText("VOLANTE DE ESTUDO — 7 DEZENAS",42,137,p);
    HashSet<Integer> selected=new HashSet<>();for(int n:chosen)selected.add(n);
    for(int n=1;n<=31;n++){int idx=n-1,col=idx%7,row=idx/7;float x=55+col*70,y=180+row*70;
      p.setColor(selected.contains(n)?Color.rgb(255,215,0):Color.rgb(235,235,235));c.drawRoundRect(x,y,x+50,y+50,10,10,p);
      p.setColor(Color.BLACK);p.setTextSize(19);c.drawText(String.format(java.util.Locale.US,"%02d",n),x+12,y+32,p);
    }
    p.setTextSize(18);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));c.drawText("MÊS DA SORTE: "+months[month-1],43,570,p);
    p.setTypeface(Typeface.DEFAULT);p.setTextSize(12);c.drawText("Dezenas em amarelo = seleção sugerida para jogar.",43,610,p);
    c.drawText("Estudo estatístico: não garante premiação.",43,634,p);
    c.drawText("Modelo para estudo — não é comprovante de aposta.",43,665,p);
    doc.finishPage(page);OutputStream out=getContentResolver().openOutputStream(uri);if(out==null)throw new IOException("Sem acesso ao destino");doc.writeTo(out);out.close();doc.close();say("PDF salvo com sucesso na pasta que você escolheu no Android.");
  }
}
